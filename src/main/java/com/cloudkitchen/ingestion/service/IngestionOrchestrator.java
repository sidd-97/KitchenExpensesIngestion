package com.cloudkitchen.ingestion.service;

import com.cloudkitchen.ingestion.discovery.FileDiscoveryService;
import com.cloudkitchen.ingestion.filename.FileNameParser;
import com.cloudkitchen.ingestion.filename.ParsedFileName;
import com.cloudkitchen.ingestion.model.FileMetadata;
import com.cloudkitchen.ingestion.model.IngestionResult;
import com.cloudkitchen.ingestion.model.ProcessingResult;
import com.cloudkitchen.ingestion.model.enums.FileOrigin;
import com.cloudkitchen.ingestion.model.enums.FileStatus;
import com.cloudkitchen.ingestion.model.enums.SourceType;
import com.cloudkitchen.ingestion.model.enums.ProcessingType;
import com.cloudkitchen.ingestion.parser.FileProcessor;
import com.cloudkitchen.ingestion.repository.FileMetadataRepository;
import com.cloudkitchen.ingestion.router.FileProcessingRouter;
import com.cloudkitchen.ingestion.source.FileSourceStrategy;
import com.cloudkitchen.ingestion.source.LocalFileSource;
import com.cloudkitchen.ingestion.source.S3FileSource;
import com.cloudkitchen.ingestion.model.FileInput;
import com.cloudkitchen.ingestion.model.enums.InputSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * MODIFIED: orchestration logic completely rewritten for new design.
 *
 * Flow per file:
 *  1. Parse filename → source, type, date range, idempotency key
 *  2. Check duplicate (idempotency key in file_metadata) → reject if exists
 *  3. Insert file_metadata (status=PROCESSING) — this always commits
 *  4. Read file bytes
 *  5. Route to correct FileProcessor
 *  6. Processor runs template method: extract → validate dates → map → score → persist (REQUIRES_NEW tx)
 *  7. Update file_metadata to PROCESSED or FAILED/REVIEW
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionOrchestrator {

    private final FileDiscoveryService   discoveryService;
    private final LocalFileSource        localFileSource;
    private final S3FileSource           s3FileSource;
    private final FileNameParser         fileNameParser;
    private final FileProcessingRouter   router;
    private final FileMetadataRepository fileMetadataRepo;
    private final IngestionEventPublisher eventPublisher;

    // ── Public API (called by scheduler and controller) ───────────────────

    public List<IngestionResult> runFullIngestion() {
        List<FileInput> files = discoveryService.discoverUnprocessed();
        if (files.isEmpty()) {
            log.info("No files present to process. Stopping ingestion.");
            return List.of();
        }
        log.info("Full ingestion: {} unprocessed files found", files.size());
        return files.stream().map(this::processFile).toList();
    }

    public IngestionResult ingestLocalFile(String absPath, SourceType sourceType,
                                           ProcessingType processingType) throws Exception {
        var attrs = java.nio.file.Files.readAttributes(
                java.nio.file.Path.of(absPath),
                java.nio.file.attribute.BasicFileAttributes.class);
        return processFile(FileInput.local(absPath, sourceType,
                attrs.lastModifiedTime().toInstant(), attrs.size()));
    }

    public IngestionResult ingestS3File(String bucket, String key,
                                        SourceType sourceType) {
        var head = s3FileSource.headObject(bucket, key);
        return processFile(FileInput.s3(bucket, key, sourceType,
                head.lastModified(), head.contentLength()));
    }

    // ── Core pipeline ─────────────────────────────────────────────────────

    private IngestionResult processFile(FileInput fi) {
        eventPublisher.fileDiscovered(fi);

        // Step 1: Parse filename
        ParsedFileName parsed;
        try {
            parsed = fileNameParser.parse(fi.getFilename());
        } catch (Exception e) {
            log.error("Filename parsing failed for {}: {}", fi.getFilename(), e.getMessage());
            return IngestionResult.failed(fi.getPath(), "Invalid filename: " + e.getMessage());
        }

        // Step 2: Idempotency check — reject duplicate before any DB write
        if (fileMetadataRepo.existsByIdempotencyKey(parsed.getIdempotencyKey())) {
            log.warn("Duplicate file rejected: {} (key={})", fi.getFilename(), parsed.getIdempotencyKey());
            return IngestionResult.skipped(fi.getPath(),
                    "Duplicate: same source/type/date-range already processed");
        }

        // Step 3: Insert file_metadata with PROCESSING status (always commits — survives data rollback)
        FileMetadata meta = FileMetadata.builder()
                .fileName(fi.getFilename())
                .source(parsed.getSource())
                .processingType(parsed.getProcessingType())
                .fileOrigin(fi.getInputSource() == InputSource.S3 ? FileOrigin.S3 : FileOrigin.LOCAL)
                .idempotencyKey(parsed.getIdempotencyKey())
                .reportStartDate(parsed.getStartDate())
                .reportEndDate(parsed.getEndDate())
                .status(FileStatus.PROCESSING)
                .build();

        Optional<Long> fileMetadataId = fileMetadataRepo.insertIfNew(meta);
        if (fileMetadataId.isEmpty()) {
            // race condition: another process inserted between check and insert
            return IngestionResult.skipped(fi.getPath(), "Concurrent duplicate detected");
        }
        Long metaId = fileMetadataId.get();

        // Step 4: Read file bytes
        byte[] content;
        try {
            FileSourceStrategy source = resolveSource(fi.getInputSource());
            try (InputStream in = source.openStream(fi)) {
                content = in.readAllBytes();
            }
        } catch (Exception e) {
            fileMetadataRepo.updateStatus(metaId, FileStatus.FAILED, 0, 0, 0, "File read error: " + e.getMessage());
            return IngestionResult.failed(fi.getPath(), "File read error: " + e.getMessage());
        }

        // Step 5: Route and process
        try {
            FileProcessor processor = router.route(parsed.getSource(), parsed.getProcessingType());
            ProcessingResult result  = processor.process(content, parsed, metaId);

            fileMetadataRepo.updateStatus(metaId, FileStatus.PROCESSED,
                    result.totalRows(), result.processedRows(), result.failedRows(), null);

            IngestionResult ingestionResult = new IngestionResult(
                    fi.getPath(), result.totalRows(), result.processedRows(),
                    0, result.failedRows(), result.errors());
            eventPublisher.fileProcessed(fi, ingestionResult);
            return ingestionResult;

        } catch (Exception e) {
            // Check if it's a "null dates" review case
            FileStatus status = e.getMessage() != null && e.getMessage().contains("Null dates")
                    ? FileStatus.REVIEW : FileStatus.FAILED;

            fileMetadataRepo.updateStatus(metaId, status, 0, 0, 0, e.getMessage());
            log.error("Processing failed for {} (status={}): {}", fi.getFilename(), status, e.getMessage());
            eventPublisher.ingestionFailed(fi, e.getMessage());
            return IngestionResult.failed(fi.getPath(), e.getMessage());
        }
    }

    private FileSourceStrategy resolveSource(InputSource is) {
        return is == InputSource.S3 ? s3FileSource : localFileSource;
    }
}
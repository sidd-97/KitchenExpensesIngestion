package com.cloudkitchen.ingestion.parser;

import com.cloudkitchen.ingestion.exception.IngestionException;
import com.cloudkitchen.ingestion.filename.ParsedFileName;
import com.cloudkitchen.ingestion.model.DateRange;
import com.cloudkitchen.ingestion.model.ProcessingResult;
import com.cloudkitchen.ingestion.model.enums.FileOrigin;
import com.cloudkitchen.ingestion.service.ConfidenceScorer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Template Method Pattern:
 * process() is the fixed algorithm.
 * extractRows(), mapRow(), extractDate(), scoreRecord(), persistBatch() are overridden per source.
 *
 * Transaction strategy:
 *   - Data rows inserted in a REQUIRES_NEW transaction.
 *   - If any exception occurs → full rollback of all rows.
 *   - file_metadata status is updated by the caller (orchestrator) after this method returns.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractFileProcessor<T> implements FileProcessor {

    protected final ConfidenceScorer        confidenceScorer;
    protected final PlatformTransactionManager transactionManager;

    @Override
    public final ProcessingResult process(byte[] content, ParsedFileName meta, Long fileMetadataId, FileOrigin fileOrigin) throws Exception {  // MODIFIED: added fileOrigin

        log.info("[{}] Starting process for file: {}", getClass().getSimpleName(), meta.getOriginalFileName());

        // Step 1: Extract raw rows from CSV / XLSX
        List<Map<String, String>> rawRows = extractRows(content, meta);
        log.info("[{}] Extracted {} raw rows", getClass().getSimpleName(), rawRows.size());

        if (rawRows.isEmpty()) {
            throw new IngestionException("File has no data rows: " + meta.getOriginalFileName());
        }

        // Step 2: Check for null dates across all rows
        List<Integer> nullDateRows = findNullDateRows(rawRows);
        if (!nullDateRows.isEmpty()) {
            throw new IngestionException(
                    "Null dates found in rows: " + nullDateRows +
                            ". File flagged for review: " + meta.getOriginalFileName());
        }

        // Step 3: Map raw rows to domain objects
        List<T> records = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < rawRows.size(); i++) {
            try {
                T record = mapRow(rawRows.get(i), fileMetadataId,
                        meta.getOriginalFileName(), fileOrigin.name());    // FIXED: was meta.getSource().name()
                records.add(record);
            } catch (Exception e) {
                log.warn("[{}] Row {} mapping failed: {}", getClass().getSimpleName(), i + 1, e.getMessage());
                errors.add("Row " + (i + 1) + ": " + e.getMessage());
            }
        }

        // Step 4: Score each record
        records.forEach(this::scoreRecord);

        // Step 5: Persist all records in a single transaction — rollback all on any failure
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus txStatus = transactionManager.getTransaction(def);
        try {
            persistBatch(records, fileMetadataId);
            transactionManager.commit(txStatus);
            log.info("[{}] Committed {} records", getClass().getSimpleName(), records.size());
        } catch (Exception e) {
            transactionManager.rollback(txStatus);
            log.error("[{}] Transaction rolled back: {}", getClass().getSimpleName(), e.getMessage());
            throw new IngestionException("Batch insert failed — all rows rolled back: " + e.getMessage(), e);
        }

        return new ProcessingResult(rawRows.size(), records.size(), errors.size(), errors);
    }

    // ── Abstract steps (implement per source) ─────────────────────────────

    /** Parse file bytes into a list of raw string maps (header → cell value). */
    protected abstract List<Map<String, String>> extractRows(byte[] content, ParsedFileName meta) throws Exception;

    /** Map one raw row to the domain object T. */
    protected abstract T mapRow(Map<String, String> row, Long fileMetadataId,
                                String fileName, String fileOrigin);

    /** Return the raw cell value for the primary date column of this source. */
    protected abstract String extractDateValue(Map<String, String> row);

    /** Apply confidence scoring rules to the record. */
    protected abstract void scoreRecord(T record);

    /** Batch-insert all records using JDBC. Called inside a transaction. */
    protected abstract void persistBatch(List<T> records, Long fileMetadataId);

    // ── Shared utility ─────────────────────────────────────────────────────

    private List<Integer> findNullDateRows(List<Map<String, String>> rows) {
        List<Integer> nullRows = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            String dateVal = extractDateValue(rows.get(i));
            if (dateVal == null || dateVal.isBlank()) {
                nullRows.add(i + 1); // 1-based row number for human readability
            }
        }
        return nullRows;
    }

    protected DateRange computeDateRange(List<Map<String, String>> rows) {
        LocalDate min = null, max = null;
        for (Map<String, String> row : rows) {
            LocalDate d = parseToLocalDate(extractDateValue(row));
            if (d == null) continue;
            if (min == null || d.isBefore(min)) min = d;
            if (max == null || d.isAfter(max))  max = d;
        }
        return DateRange.of(min, max);
    }

    protected abstract LocalDate parseToLocalDate(String rawValue);
}
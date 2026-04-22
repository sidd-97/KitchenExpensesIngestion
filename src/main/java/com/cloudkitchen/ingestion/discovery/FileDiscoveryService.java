package com.cloudkitchen.ingestion.discovery;

import com.cloudkitchen.ingestion.model.FileInput;
import com.cloudkitchen.ingestion.model.enums.InputSource;
import com.cloudkitchen.ingestion.source.FileSourceStrategy;
import com.cloudkitchen.ingestion.source.LocalFileSource;
import com.cloudkitchen.ingestion.source.S3FileSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Discovers new files across all registered paths.
 * Priority ordering: paths with higher historical file frequency are scanned first.
 * "Latest first": within a path, files are ordered by lastModified descending.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileDiscoveryService {

    private final PathRegistry pathRegistry;
    private final LocalFileSource localFileSource;
    private final S3FileSource s3FileSource;
    private final JdbcTemplate jdbc;

    /**
     * Discover all unprocessed files across all enabled paths,
     * ordered by path frequency (most active paths first), then by file recency.
     */
    public List<FileInput> discoverUnprocessed() {
        // 1. Refresh path priorities based on audit log frequency
        //refreshPathPriorities();

        List<FileInput> allFiles = new ArrayList<>();

        // 2. Scan paths in priority order
        for (PathConfig path : pathRegistry.getEnabledSorted()) {
            try {
                FileSourceStrategy source = resolveSource(path.getInputSource());
                List<FileInput> files = source.listFiles(path.getLocation(), path.getSourceType());

                // 3. Sort newest-modified first within each path
                List<FileInput> sorted = files.stream()
                        .sorted(Comparator.comparing(FileInput::getLastModified).reversed())
                        .toList();

                log.debug("Path {} yielded {} candidate files", path.getLocation(), sorted.size());
                allFiles.addAll(sorted);
            } catch (Exception e) {
                log.error("Failed to scan path {}: {}", path.getLocation(), e.getMessage());
            }
        }

        // 4. Filter out files whose hash is already in the audit log as PROCESSED
        /*Set<String> processedHashes = fetchProcessedHashes();
        return allFiles.stream()
                .filter(f -> {
                    try {
                        String hash = resolveSource(f.getInputSource()).computeHash(f);
                        return !processedHashes.contains(hash);
                    } catch (Exception e) {
                        log.warn("Could not compute hash for {}, including it anyway", f.getPath());
                        return true;
                    }
                })
                .toList();*/
        return allFiles;
    }

    /**
     * Recalculates priority for each registered path using the audit log.
     * Paths with more processed files get a lower priority number (= higher priority).
     */
    private void refreshPathPriorities() {
        String sql = """
            SELECT path_prefix, COUNT(*) AS cnt
            FROM file_audit_log
            WHERE scan_status = 'PROCESSED'
            GROUP BY path_prefix
            ORDER BY cnt DESC
            """;

        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        Map<String, Integer> freqMap = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            freqMap.put((String) rows.get(i).get("path_prefix"), i + 1);
        }

        // Rebuild registry with updated priorities
        for (PathConfig cfg : pathRegistry.getAll()) {
            int newPriority = freqMap.getOrDefault(cfg.getLocation(), 100);
            if (newPriority != cfg.getPriority()) {
                pathRegistry.disable(cfg.getId());  // remove old
                pathRegistry.register(cfg.getInputSource(), cfg.getLocation(), cfg.getSourceType());
                log.debug("Updated priority for {} → {}", cfg.getLocation(), newPriority);
            }
        }
    }

    private Set<String> fetchProcessedHashes() {
        String sql = "SELECT file_hash FROM file_audit_log WHERE scan_status IN ('PROCESSED','SKIPPED')";
        List<String> hashes = jdbc.queryForList(sql, String.class);
        return new HashSet<>(hashes);
    }

    private FileSourceStrategy resolveSource(InputSource inputSource) {
        return inputSource == InputSource.S3 ? s3FileSource : localFileSource;
    }
}
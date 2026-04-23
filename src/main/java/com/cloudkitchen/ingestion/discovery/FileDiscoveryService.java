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

@Slf4j
@Service
@RequiredArgsConstructor
public class FileDiscoveryService {

    private final PathRegistry    pathRegistry;
    private final LocalFileSource localFileSource;
    private final S3FileSource    s3FileSource;
    private final JdbcTemplate    jdbc;

    /**
     * Discovers all unprocessed files across all enabled paths.
     *
     * Order of operations:
     *  1. Refresh path priorities using file_metadata (most active paths first)
     *  2. Scan each path in priority order, sort files newest-first within each path
     *  3. Filter out files whose path already exists in file_metadata as PROCESSED or SKIPPED
     */
    public List<FileInput> discoverUnprocessed() {
        // Step 1: Re-rank paths based on historical activity in file_metadata
        // FIXED: was querying file_audit_log (old table) — now queries file_metadata
        refreshPathPriorities();

        List<FileInput> all = new ArrayList<>();

        // Step 2: Scan paths in priority order
        for (PathConfig path : pathRegistry.getEnabledSorted()) {
            try {
                List<FileInput> found = resolveSource(path.getInputSource())
                        .listFiles(path.getLocation(), path.getSourceType());

                // Newest files first within each path
                found.stream()
                        .sorted(Comparator.comparing(FileInput::getLastModified).reversed())
                        .forEach(all::add);

                log.debug("Path [{}] yielded {} candidate files", path.getLocation(), found.size());
            } catch (Exception e) {
                log.error("Failed scanning path [{}]: {}", path.getLocation(), e.getMessage());
            }
        }

        if (all.isEmpty()) {
            log.info("No files present to process across {} configured paths",
                    pathRegistry.getEnabledSorted().size());
            return List.of();
        }

        // Step 3: Filter out already-processed files
        // FIXED: was querying file_audit_log — now queries file_metadata
        Set<String> alreadyProcessed = fetchProcessedFilePaths();
        List<FileInput> unprocessed = all.stream()
                .filter(fi -> !alreadyProcessed.contains(fi.getPath()))
                .toList();

        log.info("Discovery complete: {} total candidates, {} already processed, {} to process",
                all.size(), all.size() - unprocessed.size(), unprocessed.size());

        return unprocessed;
    }

    /**
     * Re-ranks registered paths by how many files they have historically produced.
     * Paths with more PROCESSED records in file_metadata get a lower priority number (= higher priority).
     *
     * FIXED: was querying non-existent file_audit_log table.
     *        Now correctly queries file_metadata using path_prefix column.
     */
    private void refreshPathPriorities() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT path_prefix, COUNT(*) AS cnt
                FROM file_metadata
                WHERE status = 'PROCESSED'
                  AND path_prefix IS NOT NULL
                GROUP BY path_prefix
                ORDER BY cnt DESC
                """);

            int priority = 1;
            for (Map<String, Object> row : rows) {
                String prefix = (String) row.get("path_prefix");
                int p = priority++;
                pathRegistry.getAll().stream()
                        .filter(c -> c.getLocation().equals(prefix))
                        .forEach(c -> pathRegistry.updatePriority(c.getId(), p));
            }

            if (!rows.isEmpty()) {
                log.debug("Path priorities refreshed based on {} distinct path prefixes", rows.size());
            }
        } catch (Exception e) {
            // Non-fatal: if priority refresh fails, default priority (100) is used for all paths
            log.error("Failed to refresh path priorities: {}", e.getMessage());
        }
    }

    /**
     * Returns the set of file paths that have already been fully processed or deliberately skipped.
     * Used to avoid re-processing files that are still physically present in the inbox folder.
     *
     * FIXED: was querying non-existent file_audit_log table.
     *        Now correctly queries file_metadata using file_path column.
     */
    private Set<String> fetchProcessedFilePaths() {
        try {
            List<String> paths = jdbc.queryForList("""
                SELECT file_path
                FROM file_metadata
                WHERE status IN ('PROCESSED', 'SKIPPED', 'DUPLICATE')
                  AND file_path IS NOT NULL
                """, String.class);
            return new HashSet<>(paths);
        } catch (Exception e) {
            log.error("Failed to fetch processed file paths: {}", e.getMessage());
            return new HashSet<>(); // safe fallback: process everything (idempotency key is the final guard)
        }
    }

    private FileSourceStrategy resolveSource(InputSource is) {
        return is == InputSource.S3 ? s3FileSource : localFileSource;
    }
}
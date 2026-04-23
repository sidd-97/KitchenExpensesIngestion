package com.cloudkitchen.ingestion.repository;

import com.cloudkitchen.ingestion.model.FileMetadata;
import com.cloudkitchen.ingestion.model.enums.FileStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class FileMetadataRepository {

    private final JdbcTemplate jdbc;

    /**
     * Insert a new file_metadata record and return the generated ID.
     * Returns empty if the idempotency key already exists (duplicate).
     *
     * MODIFIED: now includes file_path and path_prefix columns.
     */
    public Optional<Long> insertIfNew(FileMetadata meta) {
        String sql = """
            INSERT INTO file_metadata
                (file_name, file_path, path_prefix, source, processing_type,
                 file_origin, idempotency_key, report_start_date, report_end_date,
                 status, total_rows, processed_rows, failed_rows, created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1,  meta.getFileName());
                ps.setString(2,  meta.getFilePath());        // ADDED
                ps.setString(3,  meta.getPathPrefix());      // ADDED
                ps.setString(4,  meta.getSource().name());
                ps.setString(5,  meta.getProcessingType().name());
                ps.setString(6,  meta.getFileOrigin().name());
                ps.setString(7,  meta.getIdempotencyKey());
                ps.setObject(8,  meta.getReportStartDate() != null
                        ? Date.valueOf(meta.getReportStartDate()) : null);
                ps.setObject(9,  meta.getReportEndDate() != null
                        ? Date.valueOf(meta.getReportEndDate()) : null);
                ps.setString(10, meta.getStatus().name());
                ps.setInt(11,    meta.getTotalRows());
                ps.setInt(12,    meta.getProcessedRows());
                ps.setInt(13,    meta.getFailedRows());
                ps.setTimestamp(14, Timestamp.from(meta.getCreatedAt()));
                return ps;
            }, keyHolder);
            Number key = keyHolder.getKey();
            return key != null ? Optional.of(key.longValue()) : Optional.empty();
        } catch (DuplicateKeyException e) {
            return Optional.empty(); // idempotency_key already exists → duplicate
        }
    }

    public void updateStatus(Long id, FileStatus status, int totalRows,
                             int processedRows, int failedRows, String errorMessage) {
        jdbc.update("""
            UPDATE file_metadata
            SET status=?, total_rows=?, processed_rows=?, failed_rows=?,
                error_message=?, processed_at=?
            WHERE id=?
            """,
                status.name(), totalRows, processedRows, failedRows,
                errorMessage, Timestamp.from(Instant.now()), id);
    }

    public void updateDateRange(Long id, LocalDate startDate, LocalDate endDate) {
        jdbc.update("""
            UPDATE file_metadata
            SET report_start_date=?, report_end_date=?
            WHERE id=?
            """,
                startDate != null ? Date.valueOf(startDate) : null,
                endDate   != null ? Date.valueOf(endDate)   : null,
                id);
    }

    public boolean existsByIdempotencyKey(String key) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM file_metadata WHERE idempotency_key=?",
                Integer.class, key);
        return count != null && count > 0;
    }
}
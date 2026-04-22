package com.cloudkitchen.ingestion.repository;

import com.cloudkitchen.ingestion.model.OfflineOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class OfflineOrderRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper  objectMapper;

    public void batchInsert(List<OfflineOrder> records) {
        String sql = """
            INSERT INTO offline_orders (
                file_metadata_id, file_name, source, file_origin,
                order_id, order_hash_key, row_number,
                raw_data, confidence_score, review_flags, created_at
            ) VALUES (?,?,?,?,?,?,?,?::jsonb,?,?::jsonb,?)
            """;

        jdbc.batchUpdate(sql, records, records.size(), (ps, r) -> {
            ps.setLong(1,   r.getFileMetadataId());
            ps.setString(2, r.getFileName());
            ps.setString(3, "OFFLINE");
            ps.setString(4, r.getFileOrigin());
            ps.setString(5, r.getOrderId());
            ps.setString(6, r.getOrderHashKey());
            ps.setInt(7,    r.getRowNumber());
            ps.setString(8, toJson(r.getRawData()));
            ps.setDouble(9, r.getConfidenceScore());
            ps.setString(10, r.getReviewFlags() != null ? r.getReviewFlags().toString() : "[]");
            ps.setTimestamp(11, Timestamp.from(Instant.now()));
        });
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { return "{}"; }
    }
}
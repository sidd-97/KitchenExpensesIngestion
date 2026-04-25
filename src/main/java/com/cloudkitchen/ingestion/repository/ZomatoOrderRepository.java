package com.cloudkitchen.ingestion.repository;

import com.cloudkitchen.ingestion.model.ZomatoOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ZomatoOrderRepository {

    private final JdbcTemplate jdbc;
    // ADDED: ObjectMapper to serialize review_flags to valid JSON array
    private final ObjectMapper  objectMapper;

    public void batchInsert(List<ZomatoOrder> records) {
        // FIXED: replaced ?::jsonb with plain ? for review_flags.
        // Column count: 37. Placeholder count: 37. They now match exactly.
        String sql = """
            INSERT INTO zomato_orders (
                file_metadata_id, file_name, source, file_origin,
                restaurant_id, restaurant_name, subzone, city,
                order_id, order_placed_at, order_status, delivery_type,
                distance_km, items_in_order, instructions,
                discount_construct, bill_subtotal, packaging_charges,
                restaurant_discount_promo, restaurant_discount_others,
                gold_discount, brand_pack_discount, total,
                cancellation_rejection_reason,
                restaurant_compensation_cancellation,
                restaurant_penalty_rejection,
                kpt_duration_minutes, rider_wait_time_minutes,
                order_ready_marked, rating, review,
                customer_complaint_tag, customer_id, customer_phone,
                confidence_score, review_flags, created_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

        jdbc.batchUpdate(sql, records, records.size(), (ps, r) -> {
            ps.setLong(1,    r.getFileMetadataId());
            ps.setString(2,  r.getFileName());
            ps.setString(3,  "ZOMATO");
            ps.setString(4,  r.getFileOrigin());
            ps.setString(5,  r.getRestaurantId());
            ps.setString(6,  r.getRestaurantName());
            ps.setString(7,  r.getSubzone());
            ps.setString(8,  r.getCity());
            ps.setString(9,  r.getOrderId());
            ps.setTimestamp(10, r.getOrderPlacedAt() != null ? Timestamp.from(r.getOrderPlacedAt()) : null);
            ps.setString(11, r.getOrderStatus());
            ps.setString(12, r.getDeliveryType());
            ps.setBigDecimal(13, r.getDistanceKm());
            ps.setString(14, r.getItemsInOrder());
            ps.setString(15, r.getInstructions());
            ps.setString(16, r.getDiscountConstruct());
            ps.setBigDecimal(17, r.getBillSubtotal());
            ps.setBigDecimal(18, r.getPackagingCharges());
            ps.setBigDecimal(19, r.getRestaurantDiscountPromo());
            ps.setBigDecimal(20, r.getRestaurantDiscountOthers());
            ps.setBigDecimal(21, r.getGoldDiscount());
            ps.setBigDecimal(22, r.getBrandPackDiscount());
            ps.setBigDecimal(23, r.getTotal());
            ps.setString(24, r.getCancellationRejectionReason());
            ps.setBigDecimal(25, r.getRestaurantCompensationCancellation());
            ps.setBigDecimal(26, r.getRestaurantPenaltyRejection());
            ps.setObject(27, r.getKptDurationMinutes());
            ps.setObject(28, r.getRiderWaitTimeMinutes());
            ps.setString(29, r.getOrderReadyMarked());
            ps.setBigDecimal(30, r.getRating());
            ps.setString(31, r.getReview());
            ps.setString(32, r.getCustomerComplaintTag());
            ps.setString(33, r.getCustomerId());
            ps.setString(34, r.getCustomerPhone());
            ps.setDouble(35, r.getConfidenceScore());
            // FIXED: toJson() produces valid JSON array
            ps.setObject(36, toJson(r.getReviewFlags()), Types.OTHER);
            ps.setTimestamp(37, Timestamp.from(Instant.now()));
        });
    }

    // ADDED: produces valid JSON array ["item1","item2"] not [item1, item2]
    private String toJson(Object obj) {
        if (obj == null) return "[]";
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) {
            log.warn("Failed to serialize to JSON: {}", e.getMessage());
            return "[]";
        }
    }
}
package com.cloudkitchen.ingestion.repository;

import com.cloudkitchen.ingestion.model.SwiggyOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class SwiggyOrderRepository {

    private final JdbcTemplate jdbc;

    public void batchInsert(List<SwiggyOrder> records) {
        // FIXED: replaced ?::jsonb with plain ? for review_flags.
        // Column count: 38. Placeholder count: 38. They now match exactly.
        String sql = """
            INSERT INTO swiggy_orders (
                file_metadata_id, file_name, source, file_origin,
                order_id, order_status,
                order_relay_time, order_acceptance_time,
                order_delivery_time, order_cancellation_time,
                total_bill_amount, tax_restaurant, packing_charge,
                restaurant_trade_discount, restaurant_coupon_discount_share,
                restaurant_bear,
                item_sgst, item_cgst, item_igst, item_gst_inclusive,
                packaging_charge_sgst, packaging_charge_cgst,
                packaging_charge_igst, packaging_gst_inclusive,
                service_charge_sgst, service_charge_cgst,
                service_charge_igst, service_charge_gst_inclusive,
                food_prepared, edited_status, item_count, mou_type,
                cancelled_reason, cancellation_responsible_entity,
                items_detail, confidence_score, review_flags, created_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

        jdbc.batchUpdate(sql, records, records.size(), (ps, r) -> {
            ps.setLong(1,    r.getFileMetadataId());
            ps.setString(2,  r.getFileName());
            ps.setString(3,  "SWIGGY");
            ps.setString(4,  r.getFileOrigin());
            ps.setString(5,  r.getOrderId());
            ps.setString(6,  r.getOrderStatus());
            ps.setTimestamp(7,  toTs(r.getOrderRelayTime()));
            ps.setTimestamp(8,  toTs(r.getOrderAcceptanceTime()));
            ps.setTimestamp(9,  toTs(r.getOrderDeliveryTime()));
            ps.setTimestamp(10, toTs(r.getOrderCancellationTime()));
            ps.setBigDecimal(11, r.getTotalBillAmount());
            ps.setBigDecimal(12, r.getTaxRestaurant());
            ps.setBigDecimal(13, r.getPackingCharge());
            ps.setBigDecimal(14, r.getRestaurantTradeDiscount());
            ps.setBigDecimal(15, r.getRestaurantCouponDiscountShare());
            ps.setBigDecimal(16, r.getRestaurantBear());
            ps.setBigDecimal(17, r.getItemSgst());
            ps.setBigDecimal(18, r.getItemCgst());
            ps.setBigDecimal(19, r.getItemIgst());
            ps.setBigDecimal(20, r.getItemGstInclusive());
            ps.setBigDecimal(21, r.getPackagingChargeSgst());
            ps.setBigDecimal(22, r.getPackagingChargeCgst());
            ps.setBigDecimal(23, r.getPackagingChargeIgst());
            ps.setBigDecimal(24, r.getPackagingGstInclusive());
            ps.setBigDecimal(25, r.getServiceChargeSgst());
            ps.setBigDecimal(26, r.getServiceChargeCgst());
            ps.setBigDecimal(27, r.getServiceChargeIgst());
            ps.setBigDecimal(28, r.getServiceChargeGstInclusive());
            ps.setObject(29, r.getFoodPrepared());
            ps.setString(30, r.getEditedStatus());
            ps.setObject(31, r.getItemCount());
            ps.setString(32, r.getMouType());
            ps.setString(33, r.getCancelledReason());
            ps.setString(34, r.getCancellationResponsibleEntity());
            ps.setString(35, r.getItemsDetail());
            ps.setDouble(36, r.getConfidenceScore());
            // FIXED: Types.OTHER for JSONB column
            ps.setObject(37,r.getReviewFlags() != null ? r.getReviewFlags().toString() : "[]", Types.OTHER);
            ps.setTimestamp(38, Timestamp.from(Instant.now()));
        });
    }

    private Timestamp toTs(java.time.Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }
}
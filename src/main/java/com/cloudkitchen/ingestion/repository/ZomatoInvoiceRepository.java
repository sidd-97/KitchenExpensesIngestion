package com.cloudkitchen.ingestion.repository;

import com.cloudkitchen.ingestion.model.ZomatoInvoice;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ZomatoInvoiceRepository {

    private final JdbcTemplate jdbc;

    public void batchInsert(List<ZomatoInvoice> records) {
        String sql = """
            INSERT INTO zomato_invoices (
                file_metadata_id, file_name, source, file_origin,
                sno, order_id, order_date, week_no, restaurant_name, restaurant_id,
                discount_construct, mode_of_payment, order_status,
                cancellation_policy_percent, cancellation_rejection_reason,
                cancelled_rejected_state, order_type, delivery_state_code,
                subtotal, packaging_charge, delivery_charge_self_logistics,
                restaurant_discount_promo, restaurant_discount_others,
                brand_pack_subscription_fee, delivery_charge_discount_relisting,
                total_gst_from_customers, net_order_value,
                commissionable_subtotal, commissionable_packaging_charge,
                commissionable_total_gst, total_commissionable_value,
                base_service_fee_percent, base_service_fee,
                actual_order_distance_km, long_distance_enablement_fee,
                discount_long_distance_fee, discount_service_fee_30_cap,
                payment_mechanism_fee, service_fee_and_payment_mech_fee,
                taxes_on_service_fee, applicable_amount_tcs, applicable_amount_9_5,
                tax_collected_at_source, tcs_igst_amount, tds_194o_amount,
                gst_paid_by_zomato_9_5, gst_to_be_paid_by_restaurant,
                government_charges, customer_compensation_recoupment,
                delivery_charges_recovery, amount_received_cash,
                credit_debit_note_adjustment, promo_recovery_adjustment,
                extra_inventory_ads_deduction, brand_loyalty_points_redemption,
                express_order_fee, other_order_level_deductions,
                net_deductions, net_additions, order_level_payout,
                settlement_status, settlement_date, bank_utr,
                unsettled_amount, customer_id,
                confidence_score, review_flags, created_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?)
            """;

        jdbc.batchUpdate(sql, records, records.size(), (ps, r) -> {
            int i = 1;
            ps.setLong(i++,    r.getFileMetadataId());
            ps.setString(i++,  r.getFileName());
            ps.setString(i++,  "ZOMATO");
            ps.setString(i++,  r.getFileOrigin());
            ps.setObject(i++,  r.getSno());
            ps.setString(i++,  r.getOrderId());
            ps.setObject(i++,  r.getOrderDate() != null ? Date.valueOf(r.getOrderDate()) : null);
            ps.setObject(i++,  r.getWeekNo());
            ps.setString(i++,  r.getRestaurantName());
            ps.setString(i++,  r.getRestaurantId());
            ps.setString(i++,  r.getDiscountConstruct());
            ps.setString(i++,  r.getModeOfPayment());
            ps.setString(i++,  r.getOrderStatus());
            ps.setBigDecimal(i++, r.getCancellationPolicyPercent());
            ps.setString(i++,  r.getCancellationRejectionReason());
            ps.setString(i++,  r.getCancelledRejectedState());
            ps.setString(i++,  r.getOrderType());
            ps.setString(i++,  r.getDeliveryStateCode());
            ps.setBigDecimal(i++, r.getSubtotal());
            ps.setBigDecimal(i++, r.getPackagingCharge());
            ps.setBigDecimal(i++, r.getDeliveryChargeSelfLogistics());
            ps.setBigDecimal(i++, r.getRestaurantDiscountPromo());
            ps.setBigDecimal(i++, r.getRestaurantDiscountOthers());
            ps.setBigDecimal(i++, r.getBrandPackSubscriptionFee());
            ps.setBigDecimal(i++, r.getDeliveryChargeDiscountRelisting());
            ps.setBigDecimal(i++, r.getTotalGstFromCustomers());
            ps.setBigDecimal(i++, r.getNetOrderValue());
            ps.setBigDecimal(i++, r.getCommissionableSubtotal());
            ps.setBigDecimal(i++, r.getCommissionablePackagingCharge());
            ps.setBigDecimal(i++, r.getCommissionableTotalGst());
            ps.setBigDecimal(i++, r.getTotalCommissionableValue());
            ps.setBigDecimal(i++, r.getBaseServiceFeePercent());
            ps.setBigDecimal(i++, r.getBaseServiceFee());
            ps.setBigDecimal(i++, r.getActualOrderDistanceKm());
            ps.setBigDecimal(i++, r.getLongDistanceEnablementFee());
            ps.setBigDecimal(i++, r.getDiscountLongDistanceFee());
            ps.setBigDecimal(i++, r.getDiscountServiceFee30Cap());
            ps.setBigDecimal(i++, r.getPaymentMechanismFee());
            ps.setBigDecimal(i++, r.getServiceFeeAndPaymentMechFee());
            ps.setBigDecimal(i++, r.getTaxesOnServiceFee());
            ps.setBigDecimal(i++, r.getApplicableAmountTcs());
            ps.setBigDecimal(i++, r.getApplicableAmount9_5());
            ps.setBigDecimal(i++, r.getTaxCollectedAtSource());
            ps.setBigDecimal(i++, r.getTcsIgstAmount());
            ps.setBigDecimal(i++, r.getTds194oAmount());
            ps.setBigDecimal(i++, r.getGstPaidByZomato9_5());
            ps.setBigDecimal(i++, r.getGstToBePaidByRestaurant());
            ps.setBigDecimal(i++, r.getGovernmentCharges());
            ps.setBigDecimal(i++, r.getCustomerCompensationRecoupment());
            ps.setBigDecimal(i++, r.getDeliveryChargesRecovery());
            ps.setBigDecimal(i++, r.getAmountReceivedCash());
            ps.setBigDecimal(i++, r.getCreditDebitNoteAdjustment());
            ps.setBigDecimal(i++, r.getPromoRecoveryAdjustment());
            ps.setBigDecimal(i++, r.getExtraInventoryAdsDeduction());
            ps.setBigDecimal(i++, r.getBrandLoyaltyPointsRedemption());
            ps.setBigDecimal(i++, r.getExpressOrderFee());
            ps.setBigDecimal(i++, r.getOtherOrderLevelDeductions());
            ps.setBigDecimal(i++, r.getNetDeductions());
            ps.setBigDecimal(i++, r.getNetAdditions());
            ps.setBigDecimal(i++, r.getOrderLevelPayout());
            ps.setString(i++,  r.getSettlementStatus());
            ps.setObject(i++,  r.getSettlementDate() != null ? Date.valueOf(r.getSettlementDate()) : null);
            ps.setString(i++,  r.getBankUtr());
            ps.setBigDecimal(i++, r.getUnsettledAmount());
            ps.setString(i++,  r.getCustomerId());
            ps.setDouble(i++,  r.getConfidenceScore());
            ps.setString(i++,  r.getReviewFlags() != null ? r.getReviewFlags().toString() : "[]");
            ps.setTimestamp(i, Timestamp.from(Instant.now()));
        });
    }
}
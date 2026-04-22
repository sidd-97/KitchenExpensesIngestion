package com.cloudkitchen.ingestion.service;

import com.cloudkitchen.ingestion.model.OfflineOrder;
import com.cloudkitchen.ingestion.model.SwiggyOrder;
import com.cloudkitchen.ingestion.model.ZomatoInvoice;
import com.cloudkitchen.ingestion.model.ZomatoOrder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * MODIFIED: scoring rules rewritten for new source-specific models.
 * One method per source type — each has its own mandatory field rules.
 */
@Component
public class ConfidenceScorer {

    // ── Swiggy Order ──────────────────────────────────────────────────────

    public void scoreSwiggyOrder(SwiggyOrder record) {
        double score = 1.0;
        List<String> flags = new ArrayList<>();

        if (isBlank(record.getOrderId()))          { score -= 0.30; flags.add("order_id_missing"); }
        if (record.getOrderRelayTime() == null)    { score -= 0.20; flags.add("order_relay_time_missing"); }
        if (record.getTotalBillAmount() == null)   { score -= 0.20; flags.add("total_bill_amount_missing"); }
        if (isNegative(record.getTotalBillAmount())) { score -= 0.15; flags.add("total_bill_amount_negative"); }
        if (record.getItemCount() == null || record.getItemCount() <= 0) {
            score -= 0.10; flags.add("item_count_invalid");
        }
        if (isBlank(record.getOrderStatus()))      { score -= 0.05; flags.add("order_status_missing"); }

        record.setConfidenceScore(Math.max(0.0, score));
        record.setReviewFlags(flags);
    }

    // ── Zomato Order ──────────────────────────────────────────────────────

    public void scoreZomatoOrder(ZomatoOrder record) {
        double score = 1.0;
        List<String> flags = new ArrayList<>();

        if (isBlank(record.getOrderId()))          { score -= 0.30; flags.add("order_id_missing"); }
        if (record.getOrderPlacedAt() == null)     { score -= 0.20; flags.add("order_placed_at_missing"); }
        if (record.getBillSubtotal() == null)      { score -= 0.20; flags.add("bill_subtotal_missing"); }
        if (isNegative(record.getBillSubtotal()))  { score -= 0.15; flags.add("bill_subtotal_negative"); }
        if (record.getTotal() == null)             { score -= 0.10; flags.add("total_missing"); }
        if (isBlank(record.getOrderStatus()))      { score -= 0.05; flags.add("order_status_missing"); }

        record.setConfidenceScore(Math.max(0.0, score));
        record.setReviewFlags(flags);
    }

    // ── Zomato Invoice ────────────────────────────────────────────────────

    public void scoreZomatoInvoice(ZomatoInvoice record) {
        double score = 1.0;
        List<String> flags = new ArrayList<>();

        if (isBlank(record.getOrderId()))              { score -= 0.30; flags.add("order_id_missing"); }
        if (record.getOrderDate() == null)             { score -= 0.20; flags.add("order_date_missing"); }
        if (record.getOrderLevelPayout() == null)      { score -= 0.20; flags.add("order_level_payout_missing"); }
        if (record.getSubtotal() == null)              { score -= 0.10; flags.add("subtotal_missing"); }
        if (isBlank(record.getSettlementStatus()))     { score -= 0.10; flags.add("settlement_status_missing"); }
        if (record.getNetDeductions() == null
                && record.getNetAdditions() == null)   { score -= 0.10; flags.add("deductions_additions_both_missing"); }

        record.setConfidenceScore(Math.max(0.0, score));
        record.setReviewFlags(flags);
    }

    // ── Offline Order ─────────────────────────────────────────────────────

    public void scoreOfflineOrder(OfflineOrder record) {
        double score = 1.0;
        List<String> flags = new ArrayList<>();

        if (isBlank(record.getOrderId()))             { score -= 0.30; flags.add("order_id_missing"); }
        if (record.getRawData() == null
                || record.getRawData().isEmpty())     { score -= 0.50; flags.add("raw_data_empty"); }
        if (isBlank(record.getOrderHashKey()))        { score -= 0.20; flags.add("hash_key_missing"); }

        record.setConfidenceScore(Math.max(0.0, score));
        record.setReviewFlags(flags);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private boolean isBlank(String v)         { return v == null || v.isBlank(); }
    private boolean isNegative(BigDecimal v)  { return v != null && v.compareTo(BigDecimal.ZERO) < 0; }
}
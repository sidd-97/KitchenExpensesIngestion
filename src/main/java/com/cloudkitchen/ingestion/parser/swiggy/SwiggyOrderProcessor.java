package com.cloudkitchen.ingestion.parser.swiggy;

import com.cloudkitchen.ingestion.filename.ParsedFileName;
import com.cloudkitchen.ingestion.model.SwiggyOrder;
import com.cloudkitchen.ingestion.model.enums.ProcessingType;
import com.cloudkitchen.ingestion.model.enums.SourceType;
import com.cloudkitchen.ingestion.parser.AbstractFileProcessor;
import com.cloudkitchen.ingestion.repository.SwiggyOrderRepository;
import com.cloudkitchen.ingestion.service.ConfidenceScorer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
public class SwiggyOrderProcessor extends AbstractFileProcessor<SwiggyOrder> {

    // primary date column — used for null check and date range computation
    private static final String DATE_COLUMN = "Order-relay-time(ordered time)";

    private final SwiggyOrderRepository repository;

    public SwiggyOrderProcessor(ConfidenceScorer confidenceScorer,
                                PlatformTransactionManager transactionManager,
                                SwiggyOrderRepository repository) {
        super(confidenceScorer, transactionManager);
        this.repository = repository;
    }

    @Override public SourceType     supportedSource() { return SourceType.SWIGGY; }
    @Override public ProcessingType supportedType()   { return ProcessingType.ORDER; }

    @Override
    protected List<Map<String, String>> extractRows(byte[] content, ParsedFileName meta) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        try (CSVParser parser = CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .build()
                .parse(new InputStreamReader(
                        new ByteArrayInputStream(content), StandardCharsets.UTF_8))) {
            for (CSVRecord record : parser) {
                rows.add(record.toMap());
            }
        }
        return rows;
    }

    @Override
    protected SwiggyOrder mapRow(Map<String, String> row, Long fileMetadataId,
                                 String fileName, String fileOrigin) {
        return SwiggyOrder.builder()
                .fileMetadataId(fileMetadataId)
                .fileName(fileName)
                .fileOrigin(fileOrigin)
                .orderId(str(row, "OrderID"))
                .orderStatus(str(row, "Order-status"))
                .orderRelayTime(parseInstant(str(row, "Order-relay-time(ordered time)")))
                .orderAcceptanceTime(parseInstant(str(row, "Order-acceptance-time <placed_time>")))
                .orderDeliveryTime(parseInstant(str(row, "Order-delivery-time")))
                .orderCancellationTime(parseInstant(str(row, "Order-Cancellation-time")))
                .totalBillAmount(dec(row, "Total-bill-amount <bill>"))
                .taxRestaurant(dec(row, "Tax Restaurant"))
                .packingCharge(dec(row, "Packing-charge"))
                .restaurantTradeDiscount(dec(row, "Restaurant Trade Discount"))
                .restaurantCouponDiscountShare(dec(row, "Restaurant Coupon Discount Share"))
                .restaurantBear(dec(row, "Restaurant-bear"))
                .itemSgst(dec(row, "Item-SGST"))
                .itemCgst(dec(row, "Item-CGST"))
                .itemIgst(dec(row, "Item-IGST"))
                .itemGstInclusive(dec(row, "Item-GST-Inclusive"))
                .packagingChargeSgst(dec(row, "PackagingCharge-SGST"))
                .packagingChargeCgst(dec(row, "PackagingCharge-CGST"))
                .packagingChargeIgst(dec(row, "PackagingCharge-IGST"))
                .packagingGstInclusive(dec(row, "Packaging_GST_Inclusive"))
                .serviceChargeSgst(dec(row, "ServiceCharge-SGST"))
                .serviceChargeCgst(dec(row, "ServiceCharge-CGST"))
                .serviceChargeIgst(dec(row, "ServiceCharge-IGST"))
                .serviceChargeGstInclusive(dec(row, "ServiceCharge-GST-inclusive"))
                .foodPrepared(parseBool(str(row, "Food-prepared <Yes/No>")))
                .editedStatus(str(row, "Edited-status"))
                .itemCount(parseInt(str(row, "Item-count")))
                .mouType(str(row, "MOU type"))
                .cancelledReason(str(row, "Cancelled reason"))
                .cancellationResponsibleEntity(str(row, "Cancellation-responsible-entity"))
                // store the composite items column as raw text
                .itemsDetail(str(row, "Item1-name_reward_type_quantity_price+Variants+Addons"))
                .build();
    }

    @Override
    protected String extractDateValue(Map<String, String> row) {
        return row.get(DATE_COLUMN);
    }

    @Override
    protected LocalDate parseToLocalDate(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) return null;
        // Try common Swiggy timestamp formats
        for (DateTimeFormatter fmt : List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"))) {
            try {
                return LocalDate.parse(rawValue.trim().substring(0, 10),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } catch (Exception ignored) {}
        }
        return null;
    }

    @Override
    protected void scoreRecord(SwiggyOrder record) {
        confidenceScorer.scoreSwiggyOrder(record);
    }

    @Override
    protected void persistBatch(List<SwiggyOrder> records, Long fileMetadataId) {
        repository.batchInsert(records);
    }

    // ── parsing helpers ───────────────────────────────────────────────────

    private String str(Map<String, String> row, String key) {
        String v = row.get(key);
        return (v == null || v.isBlank() || v.equals("-")) ? null : v.trim();
    }

    private BigDecimal dec(Map<String, String> row, String key) {
        String v = str(row, key);
        if (v == null) return null;
        try {
            return new BigDecimal(v.replace(",", "").replace("₹", "").trim())
                    .setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) { return null; }
    }

    private Integer parseInt(String v) {
        if (v == null || v.isBlank()) return null;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return null; }
    }

    private Boolean parseBool(String v) {
        if (v == null) return null;
        return "yes".equalsIgnoreCase(v.trim());
    }

    private Instant parseInstant(String v) {
        if (v == null || v.isBlank()) return null;
        for (DateTimeFormatter fmt : List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))) {
            try {
                return java.time.LocalDateTime.parse(v.trim(), fmt)
                        .atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant();
            } catch (Exception ignored) {}
        }
        log.warn("Could not parse timestamp: {}", v);
        return null;
    }
}
package com.cloudkitchen.ingestion.parser.zomato;

import com.cloudkitchen.ingestion.filename.ParsedFileName;
import com.cloudkitchen.ingestion.model.ZomatoOrder;
import com.cloudkitchen.ingestion.model.enums.ProcessingType;
import com.cloudkitchen.ingestion.model.enums.SourceType;
import com.cloudkitchen.ingestion.parser.AbstractFileProcessor;
import com.cloudkitchen.ingestion.repository.ZomatoOrderRepository;
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
public class ZomatoOrderProcessor extends AbstractFileProcessor<ZomatoOrder> {

    private static final String DATE_COLUMN = "Order Placed At";

    private final ZomatoOrderRepository repository;

    public ZomatoOrderProcessor(ConfidenceScorer confidenceScorer,
                                PlatformTransactionManager transactionManager,
                                ZomatoOrderRepository repository) {
        super(confidenceScorer, transactionManager);
        this.repository = repository;
    }

    @Override public SourceType     supportedSource() { return SourceType.ZOMATO; }
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
    protected ZomatoOrder mapRow(Map<String, String> row, Long fileMetadataId,
                                 String fileName, String fileOrigin) {
        return ZomatoOrder.builder()
                .fileMetadataId(fileMetadataId)
                .fileName(fileName)
                .fileOrigin(fileOrigin)
                .restaurantId(str(row, "Restaurant ID"))
                .restaurantName(str(row, "Restaurant name"))
                .subzone(str(row, "Subzone"))
                .city(str(row, "City"))
                .orderId(str(row, "Order ID"))
                .orderPlacedAt(parseInstant(str(row, "Order Placed At")))
                .orderStatus(str(row, "Order Status"))
                .deliveryType(str(row, "Delivery"))
                .distanceKm(dec(row, "Distance"))
                .itemsInOrder(str(row, "Items in order"))
                .instructions(str(row, "Instructions"))
                .discountConstruct(str(row, "Discount construct"))
                .billSubtotal(dec(row, "Bill subtotal"))
                .packagingCharges(dec(row, "Packaging charges"))
                .restaurantDiscountPromo(dec(row, "Restaurant discount (Promo)"))
                .restaurantDiscountOthers(dec(row, "Restaurant discount (Flat offs, Freebies & others)"))
                .goldDiscount(dec(row, "Gold discount"))
                .brandPackDiscount(dec(row, "Brand pack discount"))
                .total(dec(row, "Total"))
                .rating(dec(row, "Rating"))
                .review(str(row, "Review"))
                .cancellationRejectionReason(str(row, "Cancellation / Rejection reason"))
                .restaurantCompensationCancellation(dec(row, "Restaurant compensation (Cancellation)"))
                .restaurantPenaltyRejection(dec(row, "Restaurant penalty (Rejection)"))
                .kptDurationMinutes(parseInt(str(row, "KPT duration (minutes)")))
                .riderWaitTimeMinutes(parseInt(str(row, "Rider wait time (minutes)")))
                .orderReadyMarked(str(row, "Order Ready Marked"))
                .customerComplaintTag(str(row, "Customer complaint tag"))
                .customerId(str(row, "Customer ID"))
                .customerPhone(str(row, "Customer Phone"))
                .build();
    }

    @Override
    protected String extractDateValue(Map<String, String> row) {
        return row.get(DATE_COLUMN);
    }

    @Override
    protected LocalDate parseToLocalDate(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) return null;
        try {
            return LocalDate.parse(rawValue.trim().substring(0, 10),
                    DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    protected void scoreRecord(ZomatoOrder record) {
        confidenceScorer.scoreZomatoOrder(record);
    }

    @Override
    protected void persistBatch(List<ZomatoOrder> records, Long fileMetadataId) {
        repository.batchInsert(records);
    }

    private String str(Map<String, String> row, String key) {
        String v = row.get(key);
        return (v == null || v.isBlank() || v.equals("-")) ? null : v.trim();
    }
    private BigDecimal dec(Map<String, String> row, String key) {
        String v = str(row, key);
        if (v == null) return null;
        try { return new BigDecimal(v.replace(",","").replace("₹","").trim())
                .setScale(2, RoundingMode.HALF_UP); }
        catch (NumberFormatException e) { return null; }
    }
    private Integer parseInt(String v) {
        if (v == null) return null;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return null; }
    }
    private Instant parseInstant(String v) {
        if (v == null) return null;
        for (DateTimeFormatter fmt : List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm:ss a",
                        java.util.Locale.ENGLISH))) {
            try {
                return java.time.LocalDateTime.parse(v.trim(), fmt)
                        .atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant();
            } catch (Exception ignored) {}
        }
        return null;
    }
}
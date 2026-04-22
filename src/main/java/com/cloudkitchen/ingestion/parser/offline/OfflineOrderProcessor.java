package com.cloudkitchen.ingestion.parser.offline;

import com.cloudkitchen.ingestion.filename.ParsedFileName;
import com.cloudkitchen.ingestion.model.OfflineOrder;
import com.cloudkitchen.ingestion.model.enums.ProcessingType;
import com.cloudkitchen.ingestion.model.enums.SourceType;
import com.cloudkitchen.ingestion.parser.AbstractFileProcessor;
import com.cloudkitchen.ingestion.repository.OfflineOrderRepository;
import com.cloudkitchen.ingestion.service.ConfidenceScorer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Pluggable offline order processor.
 *
 * WHEN OFFLINE FORMAT IS FINALISED:
 *   1. Update OfflineOrder POJO with typed fields
 *   2. Update mapRow() below to populate those fields
 *   3. Update OfflineOrderRepository.batchInsert() with proper SQL columns
 *   Everything else (AbstractFileProcessor, router, orchestrator) stays unchanged.
 */
@Slf4j
@Component
public class OfflineOrderProcessor extends AbstractFileProcessor<OfflineOrder> {

    // TODO: update this when the offline date column is finalised
    private static final String DATE_COLUMN = "Date";

    private final OfflineOrderRepository repository;

    public OfflineOrderProcessor(ConfidenceScorer confidenceScorer,
                                 PlatformTransactionManager transactionManager,
                                 OfflineOrderRepository repository) {
        super(confidenceScorer, transactionManager);
        this.repository = repository;
    }

    @Override public SourceType     supportedSource() { return SourceType.OFFLINE; }
    @Override public ProcessingType supportedType()   { return ProcessingType.ORDER; }

    @Override
    protected List<Map<String, String>> extractRows(byte[] content, ParsedFileName meta) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        try (CSVParser parser = CSVFormat.DEFAULT
                .builder().setHeader().setSkipHeaderRecord(true)
                .setTrim(true).setIgnoreEmptyLines(true)
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
    protected OfflineOrder mapRow(Map<String, String> row, Long fileMetadataId,
                                  String fileName, String fileOrigin) {
        int rowNumber = row.size(); // approximate; override with actual index in extractRows if needed
        String orderDate = row.getOrDefault(DATE_COLUMN, "UNKNOWN");
        String orderId   = "OFFLINE_" + orderDate + "_" + rowNumber;
        String hashKey   = computeRowHash(row);

        return OfflineOrder.builder()
                .fileMetadataId(fileMetadataId)
                .fileName(fileName)
                .fileOrigin(fileOrigin)
                .orderId(orderId)
                .orderHashKey(hashKey)
                .rowNumber(rowNumber)
                .rawData(new HashMap<>(row))   // store entire row as-is
                .build();
    }

    @Override
    protected String extractDateValue(Map<String, String> row) {
        return row.get(DATE_COLUMN);
    }

    @Override
    protected LocalDate parseToLocalDate(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) return null;
        for (DateTimeFormatter fmt : List.of(
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"))) {
            try { return LocalDate.parse(rawValue.trim(), fmt); }
            catch (Exception ignored) {}
        }
        return null;
    }

    @Override
    protected void scoreRecord(OfflineOrder record) {
        confidenceScorer.scoreOfflineOrder(record);
    }

    @Override
    protected void persistBatch(List<OfflineOrder> records, Long fileMetadataId) {
        repository.batchInsert(records);
    }

    private String computeRowHash(Map<String, String> row) {
        String combined = row.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce("", (a, b) -> a + "|" + b);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}
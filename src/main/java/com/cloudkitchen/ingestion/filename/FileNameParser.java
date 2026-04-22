package com.cloudkitchen.ingestion.filename;

import com.cloudkitchen.ingestion.exception.IngestionException;
import com.cloudkitchen.ingestion.model.enums.ProcessingType;
import com.cloudkitchen.ingestion.model.enums.SourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * Parses filenames following the convention:
 *   {SOURCE}_{PROCESSING_TYPE}_{YYYYMMDD}_{YYYYMMDD}[.extension]
 *
 * Examples:
 *   SWIGGY_ORDER_20260401_20260419.csv
 *   ZOMATO_INVOICE_20260323_20260329.xlsx
 *   OFFLINE_ORDER_20260401_20260430.csv
 */
@Slf4j
@Component
public class FileNameParser {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public ParsedFileName parse(String fileName) {
        // Strip extension
        String baseName = fileName.contains(".")
                ? fileName.substring(0, fileName.lastIndexOf('.'))
                : fileName;

        String[] parts = baseName.split("_");
        if (parts.length < 4) {
            throw new IngestionException(
                    "Invalid filename format. Expected SOURCE_TYPE_YYYYMMDD_YYYYMMDD, got: " + fileName);
        }

        SourceType source;
        try {
            source = SourceType.valueOf(parts[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IngestionException("Unknown source in filename: " + parts[0]);
        }

        ProcessingType processingType;
        try {
            processingType = ProcessingType.valueOf(parts[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IngestionException("Unknown processing type in filename: " + parts[1]);
        }

        LocalDate startDate;
        LocalDate endDate;
        try {
            startDate = LocalDate.parse(parts[2], DATE_FMT);
            endDate   = LocalDate.parse(parts[3], DATE_FMT);
        } catch (Exception e) {
            throw new IngestionException(
                    "Invalid date format in filename: " + parts[2] + " / " + parts[3] +
                            ". Expected YYYYMMDD.");
        }

        String idempotencyKey = computeKey(source, processingType, startDate, endDate);

        log.info("Parsed filename [{}]: source={} type={} range={} to {}",
                fileName, source, processingType, startDate, endDate);
        log.debug("Computed idempotency key: {}", idempotencyKey);

        return ParsedFileName.builder()
                .source(source)
                .processingType(processingType)
                .startDate(startDate)
                .endDate(endDate)
                .originalFileName(fileName)
                .idempotencyKey(idempotencyKey)
                .build();
    }

    private String computeKey(SourceType source, ProcessingType type,
                              LocalDate start, LocalDate end) {
        String raw = source.name() + "_" + type.name() + "_" + start + "_" + end;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IngestionException("Failed to compute idempotency key", e);
        }
    }
}
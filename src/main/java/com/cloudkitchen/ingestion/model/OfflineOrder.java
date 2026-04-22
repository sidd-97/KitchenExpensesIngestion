package com.cloudkitchen.ingestion.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pluggable offline order model.
 * rawData stores all columns as-is until the final schema is decided.
 *
 * When the offline order format is finalised:
 *   1. Add typed fields to this POJO
 *   2. Update OfflineOrderProcessor.mapRow() to populate them
 *   3. Add columns to offline_orders table and OfflineOrderRepository.save()
 *   Everything else (AbstractFileProcessor, orchestrator) stays unchanged.
 */
@Data
@Builder
public class OfflineOrder {

    private Long              fileMetadataId;
    private String            fileName;
    private String            fileOrigin;

    // system-generated
    private String            orderId;          // OFFLINE_YYYYMMDD_{rowNumber}
    private String            orderHashKey;     // SHA-256 of all row values
    private int               rowNumber;
    private Map<String,String> rawData;          // entire row stored as JSONB

    // quality
    @Builder.Default private double       confidenceScore = 1.0;
    @Builder.Default private List<String> reviewFlags     = new ArrayList<>();
}
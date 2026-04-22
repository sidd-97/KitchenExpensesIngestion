package com.cloudkitchen.ingestion.discovery;

import com.cloudkitchen.ingestion.model.enums.InputSource;
import com.cloudkitchen.ingestion.model.enums.SourceType;
import lombok.Builder;
import lombok.Value;

/**
 * Registry Pattern: represents one configured scan location.
 * Can be added at runtime via the REST API — not just from application.yml.
 */
@Value
@Builder
public class PathConfig {
    String      id;          // UUID string
    InputSource inputSource;
    String      location;    // dir path or s3://bucket/prefix
    SourceType  sourceType;
    boolean     enabled;
    int         priority;    // lower = higher priority; updated by FrequencyAnalyzer
}
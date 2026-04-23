package com.cloudkitchen.ingestion.discovery;

import com.cloudkitchen.ingestion.model.enums.InputSource;
import com.cloudkitchen.ingestion.model.enums.SourceType;
import lombok.Builder;
import lombok.Value;

/**
 * Registry Pattern: represents one configured scan location.
 * Can be added at runtime via the REST API — not just from application.yml.
 *  priority: starts at 0 for every path.
 *  As the application processes files, FileDiscoveryService increments this
 *  based on how many PROCESSED records exist in file_metadata for that path_prefix.
 *  Lower number = higher priority (scanned first).
 *  Paths with more historical activity automatically get scanned first.
 */
@Value
@Builder
public class PathConfig {
    String      id;          // UUID string
    InputSource inputSource;
    String      location;    // dir path or s3://bucket/prefix
    SourceType  sourceType;
    boolean     enabled;

    // ADDED: default 0 — increases automatically as files are processed
    @Builder.Default
    int         priority=0;    // lower = higher priority; updated by FrequencyAnalyzer
}
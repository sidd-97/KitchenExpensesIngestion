package com.cloudkitchen.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties(prefix = "app.ingestion")
public record AppProperties(
        double confidenceThreshold,
        List<ScanPathConfig> scanPaths
) {
    public record ScanPathConfig(
            String type,          // LOCAL | S3
            String location,      // directory path or s3://bucket/prefix
            String sourceHint,    // SWIGGY | ZOMATO | BANK | MANUAL_EXCEL
            boolean enabled
    ) {}
}
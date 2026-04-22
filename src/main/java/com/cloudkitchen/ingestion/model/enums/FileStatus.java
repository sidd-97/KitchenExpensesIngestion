package com.cloudkitchen.ingestion.model.enums;

public enum FileStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    FAILED,
    DUPLICATE,
    REVIEW        // Data discrepancy or any other issue which needs manual review
}

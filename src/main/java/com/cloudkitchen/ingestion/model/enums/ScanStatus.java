// ScanStatus.java
package com.cloudkitchen.ingestion.model.enums;

public enum ScanStatus {
    DISCOVERED,
    PROCESSING,
    PROCESSED,
    FAILED,
    SKIPPED          // already processed (hash match)
}
package com.cloudkitchen.ingestion.model;

import java.time.LocalDate;

/**
 * Holds the computed min/max date extracted from file data rows.
 * Used for report_start_date and report_end_date in file_metadata.
 */
public record DateRange(LocalDate startDate, LocalDate endDate) {

    public static DateRange of(LocalDate start, LocalDate end) {
        return new DateRange(start, end);
    }
}
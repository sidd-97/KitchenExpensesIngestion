package com.cloudkitchen.ingestion.model;

import java.util.List;

public record ProcessingResult(
        int totalRows,
        int processedRows,
        int failedRows,
        List<String> errors
) {
    public static ProcessingResult success(int total) {
        return new ProcessingResult(total, total, 0, List.of());
    }

    public static ProcessingResult failed(String reason) {
        return new ProcessingResult(0, 0, 0, List.of(reason));
    }
}
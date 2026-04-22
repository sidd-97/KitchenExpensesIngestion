package com.cloudkitchen.ingestion.model;

import java.util.List;

public record IngestionResult(
        String    sourceFile,
        int       totalParsed,
        int       committed,
        int       queued,
        int       skipped,
        List<String> errors
) {
    public static IngestionResult skipped(String path, String reason) {
        return new IngestionResult(path, 0, 0, 0, 1, List.of(reason));
    }

    public static IngestionResult failed(String path, String error) {
        return new IngestionResult(path, 0, 0, 0, 0, List.of(error));
    }
}
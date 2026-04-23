package com.cloudkitchen.ingestion.model;

import com.cloudkitchen.ingestion.model.enums.FileOrigin;
import com.cloudkitchen.ingestion.model.enums.FileStatus;
import com.cloudkitchen.ingestion.model.enums.ProcessingType;
import com.cloudkitchen.ingestion.model.enums.SourceType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
public class FileMetadata {
    private Long           id;
    private String         fileName;

    // ADDED: full file path and directory prefix — needed by FileDiscoveryService
    private String         filePath;
    private String         pathPrefix;

    private SourceType     source;
    private ProcessingType processingType;
    private FileOrigin     fileOrigin;
    private String         idempotencyKey; // SHA-256(source+type+startDate+endDate)
    private LocalDate      reportStartDate;
    private LocalDate      reportEndDate;

    @Builder.Default
    private FileStatus     status = FileStatus.PENDING;

    private int            totalRows;
    private int            processedRows;
    private int            failedRows;
    private String         errorMessage;

    @Builder.Default
    private Instant        createdAt = Instant.now();
    private Instant        processedAt;
}
package com.cloudkitchen.ingestion.filename;

import com.cloudkitchen.ingestion.model.enums.ProcessingType;
import com.cloudkitchen.ingestion.model.enums.SourceType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

/**
 * Result of parsing a filename like: ZOMATO_INVOICE_20260323_20260329.xlsx
 */
@Value
@Builder
public class ParsedFileName {
    SourceType     source;
    ProcessingType processingType;
    LocalDate      startDate;
    LocalDate      endDate;
    String         originalFileName;
    String         idempotencyKey;   // SHA-256(source+type+startDate+endDate)
}
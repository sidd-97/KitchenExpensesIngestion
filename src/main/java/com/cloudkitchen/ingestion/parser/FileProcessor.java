package com.cloudkitchen.ingestion.parser;

import com.cloudkitchen.ingestion.filename.ParsedFileName;
import com.cloudkitchen.ingestion.model.ProcessingResult;
import com.cloudkitchen.ingestion.model.enums.FileOrigin;
import com.cloudkitchen.ingestion.model.enums.ProcessingType;
import com.cloudkitchen.ingestion.model.enums.SourceType;

/**
 * Strategy Pattern: one interface per source+type combination.
 * Each implementation owns its parsing, validation, and persistence.
 */
public interface FileProcessor {

    ProcessingResult process(byte[] content, ParsedFileName meta, Long fileMetadataId, FileOrigin fileOrigin) throws Exception;

    SourceType     supportedSource();
    ProcessingType supportedType();
}
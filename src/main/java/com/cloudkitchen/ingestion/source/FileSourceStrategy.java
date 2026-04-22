package com.cloudkitchen.ingestion.source;

import com.cloudkitchen.ingestion.model.FileInput;
import com.cloudkitchen.ingestion.model.enums.SourceType;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Strategy Pattern: abstracts LOCAL filesystem and S3 behind a common interface.
 * Adding GCS, SFTP, or Azure Blob later = one new implementation, zero changes elsewhere.
 */
public interface FileSourceStrategy {

    /**
     * List all eligible report files under the given location.
     * @param location  directory path for LOCAL; s3://bucket/prefix for S3
     * @param sourceType hint for how to interpret discovered files
     */
    List<FileInput> listFiles(String location, SourceType sourceType) throws IOException;

    /** Open a readable stream to the file. Caller must close. */
    InputStream openStream(FileInput fileInput) throws IOException;

    /** SHA-256 hex digest — used for idempotency. */
    String computeHash(FileInput fileInput) throws IOException;

    /** Which InputSource this strategy handles. */
    com.cloudkitchen.ingestion.model.enums.InputSource handles();
}
package com.cloudkitchen.ingestion.model;

import com.cloudkitchen.ingestion.model.enums.InputSource;
import com.cloudkitchen.ingestion.model.enums.SourceType;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class FileInput {
    InputSource inputSource;   // LOCAL or S3
    SourceType  sourceType;    // SWIGGY, ZOMATO, BANK, MANUAL_EXCEL
    String      path;          // absolute local path OR s3://bucket/key
    String      filename;
    long        fileSizeBytes;
    Instant     lastModified;

    public static FileInput local(String absPath, SourceType sourceType, Instant lastModified, long size) {
        String filename = absPath.substring(absPath.lastIndexOf('/') + 1);
        return FileInput.builder()
                .inputSource(InputSource.LOCAL)
                .sourceType(sourceType)
                .path(absPath)
                .filename(filename)
                .fileSizeBytes(size)
                .lastModified(lastModified)
                .build();
    }

    public static FileInput s3(String bucket, String key, SourceType sourceType,
                               Instant lastModified, long size) {
        String filename = key.substring(key.lastIndexOf('/') + 1);
        return FileInput.builder()
                .inputSource(InputSource.S3)
                .sourceType(sourceType)
                .path("s3://" + bucket + "/" + key)
                .filename(filename)
                .fileSizeBytes(size)
                .lastModified(lastModified)
                .build();
    }
}
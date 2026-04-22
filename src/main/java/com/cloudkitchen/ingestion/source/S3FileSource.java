package com.cloudkitchen.ingestion.source;

import com.cloudkitchen.ingestion.model.FileInput;
import com.cloudkitchen.ingestion.model.enums.InputSource;
import com.cloudkitchen.ingestion.model.enums.SourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3FileSource implements FileSourceStrategy {

    private final S3Client s3Client;
    private static final List<String> SUPPORTED_EXT = List.of(".pdf", ".xlsx", ".xls", ".csv");

    @Override
    public InputSource handles() { return InputSource.S3; }

    @Override
    public List<FileInput> listFiles(String location, SourceType sourceType) {
        // location format: s3://bucket/prefix/
        String stripped = location.replace("s3://", "");
        int slash = stripped.indexOf('/');
        String bucket = slash > 0 ? stripped.substring(0, slash) : stripped;
        String prefix = slash > 0 ? stripped.substring(slash + 1) : "";

        var request = ListObjectsV2Request.builder()
                .bucket(bucket).prefix(prefix).build();

        return s3Client.listObjectsV2(request)
                .contents()
                .stream()
                .filter(obj -> SUPPORTED_EXT.stream()
                        .anyMatch(ext -> obj.key().toLowerCase().endsWith(ext)))
                .map(obj -> FileInput.s3(
                        bucket, obj.key(), sourceType,
                        obj.lastModified(), obj.size()))
                .toList();
    }

    @Override
    public InputStream openStream(FileInput fileInput) {
        String path = fileInput.getPath().replace("s3://", "");
        int slash = path.indexOf('/');
        String bucket = path.substring(0, slash);
        String key    = path.substring(slash + 1);
        return s3Client.getObject(
                GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public String computeHash(FileInput fileInput) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = openStream(fileInput)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) digest.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IOException("S3 hash failed for " + fileInput.getPath(), e);
        }
    }

    public HeadObjectResponse headObject(String bucket, String key) {
        return s3Client.headObject(
                HeadObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
package com.cloudkitchen.ingestion.source;

import com.cloudkitchen.ingestion.model.FileInput;
import com.cloudkitchen.ingestion.model.enums.InputSource;
import com.cloudkitchen.ingestion.model.enums.SourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Component
public class LocalFileSource implements FileSourceStrategy {

    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".pdf", ".xlsx", ".xls", ".csv");

    @Override
    public InputSource handles() { return InputSource.LOCAL; }

    @Override
    public List<FileInput> listFiles(String location, SourceType sourceType) throws IOException {
        Path dir = Path.of(location);
        if (!Files.isDirectory(dir)) {
            log.warn("Local path is not a directory or does not exist: {}", location);
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(dir, 1)) {
            return stream
                    .filter(p -> !Files.isDirectory(p))
                    .filter(p -> SUPPORTED_EXTENSIONS.stream()
                            .anyMatch(ext -> p.getFileName().toString().toLowerCase().endsWith(ext)))
                    .map(p -> {
                        try {
                            BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                            return FileInput.local(
                                    p.toAbsolutePath().toString(),
                                    sourceType,
                                    attrs.lastModifiedTime().toInstant(),
                                    attrs.size()
                            );
                        } catch (IOException e) {
                            log.error("Error reading attrs for {}: {}", p, e.getMessage());
                            return null;
                        }
                    })
                    .filter(f -> f != null)
                    .toList();
        }
    }

    @Override
    public InputStream openStream(FileInput fileInput) throws IOException {
        return new BufferedInputStream(new FileInputStream(fileInput.getPath()));
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
            throw new IOException("Hash computation failed for " + fileInput.getPath(), e);
        }
    }
}
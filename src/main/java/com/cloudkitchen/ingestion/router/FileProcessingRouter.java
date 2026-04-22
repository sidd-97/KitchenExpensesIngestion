package com.cloudkitchen.ingestion.router;

import com.cloudkitchen.ingestion.exception.UnsupportedFormatException;
import com.cloudkitchen.ingestion.model.enums.ProcessingType;
import com.cloudkitchen.ingestion.model.enums.SourceType;
import com.cloudkitchen.ingestion.parser.FileProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Routes a (SourceType, ProcessingType) pair to the correct FileProcessor.
 * Spring injects all FileProcessor implementations automatically.
 * Adding a new processor = create the class, annotate with @Component — nothing else changes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileProcessingRouter {

    private final List<FileProcessor> processors;

    public FileProcessor route(SourceType source, ProcessingType type) {
        return processors.stream()
                .filter(p -> p.supportedSource() == source && p.supportedType() == type)
                .findFirst()
                .orElseThrow(() -> new UnsupportedFormatException(
                        "No processor registered for source=" + source + " type=" + type +
                                ". Available: " + describeAvailable()));
    }

    private String describeAvailable() {
        return processors.stream()
                .map(p -> p.supportedSource() + "/" + p.supportedType())
                .toList()
                .toString();
    }
}
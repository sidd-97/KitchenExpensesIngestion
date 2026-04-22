package com.cloudkitchen.ingestion.service;

import com.cloudkitchen.ingestion.model.FileInput;
import com.cloudkitchen.ingestion.model.IngestionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Observer Pattern via Spring ApplicationEvents.
 * Publish events here; listeners subscribe independently (audit log, alerts, metrics).
 */
@Component
@RequiredArgsConstructor
public class IngestionEventPublisher {

    private final ApplicationEventPublisher publisher;

    public record FileDiscoveredEvent(FileInput file) {}
    public record FileProcessedEvent(FileInput file, IngestionResult result) {}
    public record IngestionFailedEvent(FileInput file, String error) {}

    public void fileDiscovered(FileInput f)                          { publisher.publishEvent(new FileDiscoveredEvent(f)); }
    public void fileProcessed(FileInput f, IngestionResult r)       { publisher.publishEvent(new FileProcessedEvent(f, r)); }
    public void ingestionFailed(FileInput f, String error)          { publisher.publishEvent(new IngestionFailedEvent(f, error)); }
}
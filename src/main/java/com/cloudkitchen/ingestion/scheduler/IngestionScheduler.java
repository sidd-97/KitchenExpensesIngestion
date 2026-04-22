package com.cloudkitchen.ingestion.scheduler;

import com.cloudkitchen.ingestion.service.IngestionOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionScheduler {

    private final IngestionOrchestrator orchestrator;

    //@Scheduled(cron = "${scheduling.ingestion.cron:0 0 8 * * MON}")
    public void scheduledRun() {
        log.info("Scheduled ingestion triggered");
        try {
            var results = orchestrator.runFullIngestion();
            long failures = results.stream().filter(r -> !r.errors().isEmpty()).count();
            log.info("Scheduled ingestion done: {} files, {} failures", results.size(), failures);
        } catch (Exception e) {
            log.error("Scheduled ingestion error: {}", e.getMessage(), e);
        }
    }
}
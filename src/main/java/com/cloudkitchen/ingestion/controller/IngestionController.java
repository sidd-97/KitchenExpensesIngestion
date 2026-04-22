package com.cloudkitchen.ingestion.controller;

import com.cloudkitchen.ingestion.discovery.PathConfig;
import com.cloudkitchen.ingestion.discovery.PathRegistry;
import com.cloudkitchen.ingestion.model.IngestionResult;
import com.cloudkitchen.ingestion.model.enums.InputSource;
import com.cloudkitchen.ingestion.model.enums.ProcessingType;
import com.cloudkitchen.ingestion.model.enums.SourceType;
import com.cloudkitchen.ingestion.service.IngestionOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MODIFIED: endpoints updated for new source/processingType model.
 * All endpoints Grafana-compatible (JSON responses, no UI).
 */
@RestController
@RequestMapping("/api/v1/ingestion")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionOrchestrator orchestrator;
    private final PathRegistry          pathRegistry;

    /** Full scan across all registered paths */
    @PostMapping("/run")
    public ResponseEntity<List<IngestionResult>> runAll() {
        return ResponseEntity.ok(orchestrator.runFullIngestion());
    }

    /** Ingest a specific local file. Filename must follow naming convention. */
    @PostMapping("/local")
    public ResponseEntity<IngestionResult> ingestLocal(
            @RequestParam String filePath,
            @RequestParam SourceType sourceType,
            @RequestParam ProcessingType processingType) throws Exception {
        return ResponseEntity.ok(
                orchestrator.ingestLocalFile(filePath, sourceType, processingType));
    }

    /** Ingest a specific S3 object. Filename must follow naming convention. */
    @PostMapping("/s3")
    public ResponseEntity<IngestionResult> ingestS3(
            @RequestParam String bucket,
            @RequestParam String key,
            @RequestParam SourceType sourceType) {
        return ResponseEntity.ok(orchestrator.ingestS3File(bucket, key, sourceType));
    }

    /** Register a new scan path at runtime */
    @PostMapping("/paths")
    public ResponseEntity<PathConfig> addPath(
            @RequestParam InputSource inputSource,
            @RequestParam String location,
            @RequestParam SourceType sourceType) {
        return ResponseEntity.ok(pathRegistry.register(inputSource, location, sourceType));
    }

    /** List all registered paths — useful for Grafana health checks */
    @GetMapping("/paths")
    public ResponseEntity<List<PathConfig>> listPaths() {
        return ResponseEntity.ok(pathRegistry.getAll());
    }

    /** Disable a path by ID */
    @DeleteMapping("/paths/{id}")
    public ResponseEntity<Void> disablePath(@PathVariable String id) {
        pathRegistry.disable(id);
        return ResponseEntity.noContent().build();
    }

    /** Health/status endpoint — for Grafana monitoring */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "activePaths", pathRegistry.getEnabledSorted().size(),
                "totalPaths", pathRegistry.getAll().size()
        ));
    }
}
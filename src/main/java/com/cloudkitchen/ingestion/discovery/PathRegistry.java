package com.cloudkitchen.ingestion.discovery;

import com.cloudkitchen.ingestion.config.AppProperties;
import com.cloudkitchen.ingestion.model.enums.InputSource;
import com.cloudkitchen.ingestion.model.enums.SourceType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class PathRegistry {

    private final AppProperties appProperties;

    // ConcurrentHashMap for thread-safe access
    private final Map<String, PathConfig> registry = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadFromConfig() {
        if (appProperties.scanPaths() == null) return;
        appProperties.scanPaths().stream()
                .filter(AppProperties.ScanPathConfig::enabled)
                .forEach(cfg -> {
                    String id = UUID.randomUUID().toString();
                    // MODIFIED: priority defaults to 0 for all paths loaded from config
                    registry.put(id, PathConfig.builder()
                            .id(id)
                            .inputSource(InputSource.valueOf(cfg.type()))
                            .location(cfg.location())
                            .sourceType(SourceType.valueOf(cfg.sourceHint()))
                            .enabled(true)
                            .priority(0)    // starts at 0, increases as files are processed
                            .build());
                    log.info("Registered scan path: {} [{}] for {} (priority=0)",
                            cfg.location(), cfg.type(), cfg.sourceHint());
                });
    }

    /**
     * Register a new path at runtime via the REST API.
     * Also starts at priority 0.
     */
    public PathConfig register(InputSource inputSource, String location,
                               SourceType sourceType) {
        String id = UUID.randomUUID().toString();
        PathConfig cfg = PathConfig.builder()
                .id(id)
                .inputSource(inputSource)
                .location(location)
                .sourceType(sourceType)
                .enabled(true)
                .priority(0)    // new paths start at 0
                .build();
        registry.put(id, cfg);
        log.info("Dynamically registered path: {} for {} (priority=0)", location, sourceType);
        return cfg;
    }

    /**
     * ADDED: Updates the priority of a path.
     * Called by FileDiscoveryService.refreshPathPriorities() after
     * querying file_metadata to rank paths by historical file count.
     *
     * PathConfig is immutable (@Value), so we rebuild it with the new priority.
     * All other fields stay unchanged.
     */
    public void updatePriority(String id, int newPriority) {
        registry.computeIfPresent(id, (k, existing) ->
                PathConfig.builder()
                        .id(existing.getId())
                        .inputSource(existing.getInputSource())
                        .location(existing.getLocation())
                        .sourceType(existing.getSourceType())
                        .enabled(existing.isEnabled())
                        .priority(newPriority)
                        .build()
        );
        log.debug("Updated priority for path id={} to {}", id, newPriority);
    }

    /**
     * Disable a path by ID.
     * Disabled paths are excluded from all future scans
     * until re-enabled or the app restarts.
     */
    public void disable(String id) {
        registry.computeIfPresent(id, (k, existing) ->
                PathConfig.builder()
                        .id(existing.getId())
                        .inputSource(existing.getInputSource())
                        .location(existing.getLocation())
                        .sourceType(existing.getSourceType())
                        .enabled(false)
                        .priority(existing.getPriority())
                        .build()
        );
        log.info("Disabled path id={}", id);
    }

    /**
     * Returns all enabled paths sorted by priority ascending.
     * Priority 0 = highest priority (scanned first).
     * Paths with the same priority are returned in insertion order.
     */
    public List<PathConfig> getEnabledSorted() {
        return registry.values().stream()
                .filter(PathConfig::isEnabled)
                .sorted(Comparator.comparingInt(PathConfig::getPriority))
                .toList();
    }

    public List<PathConfig> getAll() {
        return new ArrayList<>(registry.values());
    }
}
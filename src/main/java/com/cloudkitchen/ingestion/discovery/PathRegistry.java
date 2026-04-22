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
                    PathConfig pathConfig = PathConfig.builder()
                            .id(id)
                            .inputSource(InputSource.valueOf(cfg.type()))
                            .location(cfg.location())
                            .sourceType(SourceType.valueOf(cfg.sourceHint()))
                            .enabled(true)
                            .priority(100) // default; recalculated by FileDiscoveryService
                            .build();
                    registry.put(id, pathConfig);
                    log.info("Registered path: {} [{}] for {}", cfg.location(), cfg.type(), cfg.sourceHint());
                });
    }

    /** Add a new path at runtime without restarting the app. */
    public PathConfig register(InputSource inputSource, String location,
                               SourceType sourceType) {
        String id = UUID.randomUUID().toString();
        PathConfig cfg = PathConfig.builder()
                .id(id).inputSource(inputSource).location(location)
                .sourceType(sourceType).enabled(true).priority(100).build();
        registry.put(id, cfg);
        log.info("Dynamically registered path: {}", location);
        return cfg;
    }

    public void disable(String id) {
        registry.computeIfPresent(id, (k, v) ->
                PathConfig.builder().id(v.getId()).inputSource(v.getInputSource())
                        .location(v.getLocation()).sourceType(v.getSourceType())
                        .enabled(false).priority(v.getPriority()).build());
    }

    /** Returns all enabled paths sorted by priority (lowest number first). */
    public List<PathConfig> getEnabledSorted() {
        return registry.values().stream()
                .filter(PathConfig::isEnabled)
                .sorted(Comparator.comparingInt(PathConfig::getPriority))
                .toList();
    }

    public List<PathConfig> getAll() { return new ArrayList<>(registry.values()); }
}
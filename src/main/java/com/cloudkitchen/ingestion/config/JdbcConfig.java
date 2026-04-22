package com.cloudkitchen.ingestion.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class JdbcConfig {
    // JdbcTemplate is auto-configured by spring-boot-starter-jdbc + datasource props.
    // This class activates @ConfigurationProperties scanning.
}
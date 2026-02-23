package ru.itq.fun.generator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "generator")
public record GeneratorProperties(
        int count,
        String documentServiceUrl,
        int batchSize,
        String initiator
) {}

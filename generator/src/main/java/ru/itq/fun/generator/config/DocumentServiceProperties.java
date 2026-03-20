package ru.itq.fun.generator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "document-service")
public record DocumentServiceProperties(
    String url
) {}

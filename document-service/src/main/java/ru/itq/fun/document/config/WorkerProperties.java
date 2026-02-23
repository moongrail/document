package ru.itq.fun.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "worker")
public record WorkerProperties(
        int batchSize,
        ScheduleProps submit,
        ScheduleProps approve,
        String initiator
) {
    public record ScheduleProps(String cron) {}
}

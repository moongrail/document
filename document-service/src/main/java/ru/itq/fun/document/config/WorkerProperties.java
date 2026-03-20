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

    public WorkerProperties {
        if (batchSize == 0) batchSize = 50;
        if (submit == null) submit = new ScheduleProps("-");
        if (approve == null) approve = new ScheduleProps("-");
        if (initiator == null) initiator = "WORKER";
    }
}

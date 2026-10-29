package kz.clientflow.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Runs the maintenance job every hour. Disabled with app.scheduler.enabled=false (e.g. in tests). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {

    private final MaintenanceJob job;

    public SchedulingConfig(MaintenanceJob job) {
        this.job = job;
    }

    @Scheduled(cron = "${app.scheduler.cron:0 5 * * * *}", zone = "${app.timezone:Asia/Almaty}")
    void hourly() {
        job.run();
    }
}

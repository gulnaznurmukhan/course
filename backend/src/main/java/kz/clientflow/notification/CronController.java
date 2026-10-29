package kz.clientflow.notification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import kz.clientflow.common.ApiException;
import kz.clientflow.config.AppProperties;

/**
 * Entry point for an external scheduler (Vercel Cron sends
 * "Authorization: Bearer $CRON_SECRET"). Without a configured secret the
 * endpoint does not exist (404).
 */
@RestController
public class CronController {

    private final MaintenanceJob job;
    private final AppProperties properties;

    public CronController(MaintenanceJob job, AppProperties properties) {
        this.job = job;
        this.properties = properties;
    }

    @GetMapping("/api/v1/internal/cron/maintenance")
    MaintenanceJob.Result run(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String secret = properties.cronSecret();
        if (secret == null || secret.isBlank()) {
            throw ApiException.notFound();
        }
        byte[] expected = ("Bearer " + secret).getBytes(StandardCharsets.UTF_8);
        byte[] actual = authorization == null ? new byte[0] : authorization.getBytes(StandardCharsets.UTF_8);
        // Constant-time comparison: the response time does not reveal how much of the secret matched
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }
        return job.run();
    }
}

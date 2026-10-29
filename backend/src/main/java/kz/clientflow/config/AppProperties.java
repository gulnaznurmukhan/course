package kz.clientflow.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application settings from the "app.*" section of application.yml.
 * Values come from environment variables in production.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String publicUrl, boolean sessionCookieSecure, String timezone,
                            String cronSecret, Cors cors, Mail mail, Tokens tokens) {

    public record Cors(List<String> allowedOrigins) {
    }

    public record Mail(String from) {
    }

    /** Lifetime of single-use email tokens. */
    public record Tokens(Duration verificationTtl, Duration passwordResetTtl) {
    }
}

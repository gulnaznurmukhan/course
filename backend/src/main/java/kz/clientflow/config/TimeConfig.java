package kz.clientflow.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Business dates ("today", overdue deadlines) are calculated in the
 * configured time zone, not in the server's zone (servers usually run in UTC).
 */
@Configuration
public class TimeConfig {

    @Bean
    Clock clock(AppProperties properties) {
        return Clock.system(ZoneId.of(properties.timezone()));
    }
}

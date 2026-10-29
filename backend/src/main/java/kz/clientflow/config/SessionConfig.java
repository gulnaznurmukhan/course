package kz.clientflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Session cookie settings. Configured explicitly because Spring Session
 * writes its own cookie (server.servlet.session.cookie.* does not apply to it).
 */
@Configuration
public class SessionConfig {

    @Bean
    CookieSerializer cookieSerializer(AppProperties properties) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("CF_SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);   // not readable from JavaScript
        serializer.setSameSite("Lax");           // not sent on cross-site POST requests
        serializer.setUseSecureCookie(properties.sessionCookieSecure()); // HTTPS only in production
        return serializer;
    }
}

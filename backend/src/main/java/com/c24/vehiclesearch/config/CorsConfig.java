package com.c24.vehiclesearch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * Cross-origin access for deployments where the frontend and the API are
 * served from different hosts.
 *
 * Not needed everywhere: behind a reverse proxy the two share an origin and
 * this stays empty. It matters on a split deployment — a static site plus a
 * separate API service — where the browser will block every call without it.
 *
 * Origins are an explicit allowlist, never a wildcard. This API has a paid
 * model and a rate limiter behind it, so "any site may call this from a
 * visitor's browser" is not a default worth having.
 */
@Configuration
public class CorsConfig {

    @Bean
    CorsFilter corsFilter(
            @Value("${cors.allowed-origins:}") List<String> allowedOrigins) {

        var config = new CorsConfiguration();
        var source = new UrlBasedCorsConfigurationSource();

        if (allowedOrigins.isEmpty()) {
            // Same-origin deployment: register nothing, so no CORS headers are
            // emitted and nothing is inadvertently opened up.
            return new CorsFilter(source);
        }

        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type"));
        // No cookies or auth headers are used, so credentials stay off; that
        // also keeps the allowlist from having to be exact-origin for security
        // reasons beyond the ones above.
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/actuator/health", config);
        source.registerCorsConfiguration("/health", config);
        return new CorsFilter(source);
    }
}

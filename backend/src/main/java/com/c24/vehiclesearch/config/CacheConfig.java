package com.c24.vehiclesearch.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Parses are cached, not results. Inventory changes by the hour; the meaning
     * of "diesel automatic under 80k km" does not, so a long TTL here is safe
     * while caching the vehicles themselves would go stale and show sold cars.
     */
    @Bean
    CaffeineCacheManager cacheManager() {
        var manager = new CaffeineCacheManager("llmParses");
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofHours(6))
                .recordStats());
        return manager;
    }
}

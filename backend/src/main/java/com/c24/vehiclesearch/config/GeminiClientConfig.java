package com.c24.vehiclesearch.config;

import com.c24.vehiclesearch.search.llm.GeminiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class GeminiClientConfig {

    /**
     * Timeouts are the whole point of this bean. Without an explicit read
     * timeout a stalled provider connection holds a request thread until the
     * OS gives up, turning a degraded dependency into a degraded service.
     */
    @Bean
    RestClient geminiRestClient(GeminiProperties props) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.timeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.timeoutMs()));
        // Built from the static factory rather than an injected builder: this
        // client talks to one third-party API and should not inherit whatever
        // interceptors the application's own outbound calls acquire later.
        return RestClient.builder().requestFactory(factory).build();
    }
}

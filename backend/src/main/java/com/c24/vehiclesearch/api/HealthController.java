package com.c24.vehiclesearch.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A plain liveness check at a memorable path.
 *
 * Deliberately does nothing. It touches no database, opens no connection and
 * allocates nothing, so it answers while a dependency is down and — on a
 * platform where the database scales to zero — a keep-alive ping here wakes the
 * web service without also waking the database and spending its compute hours.
 *
 * That is the difference from {@code /actuator/health}, which validates a
 * datasource connection and returns 503 when Postgres is unreachable. Both are
 * correct for their purpose: this one answers "is the process up", the actuator
 * one answers "can this instance serve traffic", which is what a platform health
 * check should be asking before it routes to a container.
 */
@RestController
public class HealthController {

    @GetMapping(value = "/health", produces = MediaType.TEXT_PLAIN_VALUE)
    public String health() {
        return "OK";
    }
}

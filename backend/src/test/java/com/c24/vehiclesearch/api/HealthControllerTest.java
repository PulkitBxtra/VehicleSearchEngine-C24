package com.c24.vehiclesearch.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Loads only the controller, no datasource. That the test needs nothing to run
 * is the property being asserted: /health must answer while dependencies are
 * down.
 */
@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("/health answers 200 with no dependencies of any kind")
    void returnsOk() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }
}

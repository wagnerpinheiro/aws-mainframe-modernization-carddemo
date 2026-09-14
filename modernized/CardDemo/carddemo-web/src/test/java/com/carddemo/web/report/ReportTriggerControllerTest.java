package com.carddemo.web.report;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Characterization tests for CORPT00C → ReportTriggerController.
 *
 * Q10 architectural substitution: no JCL reconstruction.
 * POST /reports/trigger → HTTP 202 Accepted with jobExecutionId.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportTriggerControllerTest {

    @Autowired MockMvc mvc;

    /** POST /reports/trigger returns HTTP 202 Accepted with a jobExecutionId. */
    @Test
    @WithMockUser
    void trigger_returnsHttp202WithJobExecutionId() throws Exception {
        mvc.perform(post("/reports/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reportType":"TRANSACTION","startDate":"2024-01-01","endDate":"2024-12-31"}
                    """)
                .with(csrf()))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.jobExecutionId").exists())
            .andExpect(jsonPath("$.status").value("STARTING"))
            .andExpect(jsonPath("$.reportType").value("TRANSACTION"));
    }

    /** Missing fields are accepted gracefully (CORPT00C had optional screen fields). */
    @Test
    @WithMockUser
    void trigger_emptyBody_stillReturns202() throws Exception {
        mvc.perform(post("/reports/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(csrf()))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.jobExecutionId").exists());
    }
}

package com.jobhunt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhunt.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Analytics: metrics, funnel, monthly timeline, breakdowns, upcoming deadlines and the
 * date-range filter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String register() throws Exception {
        String email = "analytics-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Analytics Tester", email, "secret123"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private long createJob(String token, String company, String title, String status, String extraJson)
            throws Exception {
        String body = "{\"companyName\":\"" + company + "\",\"jobTitle\":\"" + title + "\",\"status\":\""
                + status + "\"" + (extraJson == null ? "" : "," + extraJson) + "}";

        MvcResult result = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void moveTo(String token, long jobId, String status) throws Exception {
        mockMvc.perform(patch("/api/jobs/{id}/status", jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void metricsAndFunnelReflectCurrentStatuses() throws Exception {
        String token = register();
        createJob(token, "Acme", "Engineer", "wishlist", null);
        createJob(token, "Acme", "Analyst", "applied", null);
        createJob(token, "Globex", "Engineer", "interview", null);
        createJob(token, "Initech", "Engineer", "offer", null);
        createJob(token, "Umbrella", "Engineer", "rejected", null);

        mockMvc.perform(get("/api/analytics").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.total").value(5))
                .andExpect(jsonPath("$.metrics.applied").value(4))
                .andExpect(jsonPath("$.metrics.interviewing").value(1))
                .andExpect(jsonPath("$.metrics.offers").value(1))
                .andExpect(jsonPath("$.metrics.rejected").value(1))
                // 3 of the 4 applications drew a response (interview, offer, rejected)
                .andExpect(jsonPath("$.metrics.responseRate").value(75.0))
                .andExpect(jsonPath("$.metrics.interviewRate").value(25.0))
                .andExpect(jsonPath("$.metrics.offerRate").value(25.0))
                // Funnel is in pipeline order
                .andExpect(jsonPath("$.funnel", hasSize(6)))
                .andExpect(jsonPath("$.funnel[0].status").value("wishlist"))
                .andExpect(jsonPath("$.funnel[0].count").value(1))
                .andExpect(jsonPath("$.funnel[4].status").value("offer"))
                .andExpect(jsonPath("$.funnel[4].count").value(1));
    }

    @Test
    void timelineCountsStageEntriesFromTheHistoryLog() throws Exception {
        String token = register();
        long jobId = createJob(token, "Acme", "Engineer", "wishlist", null);
        moveTo(token, jobId, "applied");
        moveTo(token, jobId, "interview");
        moveTo(token, jobId, "offer");

        String thisMonth = YearMonth.now().toString();

        mockMvc.perform(get("/api/analytics").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeline", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.timeline[?(@.period == '" + thisMonth + "')].applied")
                        .value(org.hamcrest.Matchers.hasItem(1)))
                .andExpect(jsonPath("$.timeline[?(@.period == '" + thisMonth + "')].interviews")
                        .value(org.hamcrest.Matchers.hasItem(1)))
                .andExpect(jsonPath("$.timeline[?(@.period == '" + thisMonth + "')].offers")
                        .value(org.hamcrest.Matchers.hasItem(1)));
    }

    @Test
    void breakdownsGroupByCompanyAndRole() throws Exception {
        String token = register();
        // Note: the same company AND title would be rejected by the duplicate guard, so the
        // second Acme row uses a different role.
        createJob(token, "Acme", "Engineer", "interview", null);
        createJob(token, "Acme", "Analyst", "applied", null);
        createJob(token, "Globex", "Analyst", "offer", null);

        mockMvc.perform(get("/api/analytics").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[0].label").value("Acme"))
                .andExpect(jsonPath("$.companies[0].total").value(2))
                .andExpect(jsonPath("$.companies[0].interviewing").value(1))
                // "Analyst" appears at two companies, so it tops the role breakdown.
                .andExpect(jsonPath("$.roles[0].label").value("Analyst"))
                .andExpect(jsonPath("$.roles[0].total").value(2))
                .andExpect(jsonPath("$.roles[1].label").value("Engineer"))
                .andExpect(jsonPath("$.roles[1].total").value(1));
    }

    @Test
    void dateRangeFilterExcludesJobsOutsideTheWindow() throws Exception {
        String token = register();
        createJob(token, "Old Corp", "Engineer", "applied", "\"appliedDate\":\"2020-01-15\"");
        createJob(token, "New Corp", "Engineer", "applied", "\"appliedDate\":\""
                + LocalDate.now() + "\"");

        mockMvc.perform(get("/api/analytics")
                        .param("from", LocalDate.now().minusMonths(1).toString())
                        .param("to", LocalDate.now().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.total").value(1))
                .andExpect(jsonPath("$.companies", hasSize(1)))
                .andExpect(jsonPath("$.companies[0].label").value("New Corp"));
    }

    @Test
    void anInvertedRangeIsRejected() throws Exception {
        String token = register();

        mockMvc.perform(get("/api/analytics")
                        .param("from", "2026-06-01")
                        .param("to", "2026-01-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upcomingDeadlinesRespectTheHorizonAndAreSorted() throws Exception {
        String token = register();
        createJob(token, "Soon Corp", "Engineer", "applied",
                "\"deadline\":\"" + LocalDate.now().plusDays(5) + "\"");
        createJob(token, "Later Corp", "Engineer", "applied",
                "\"deadline\":\"" + LocalDate.now().plusDays(20) + "\"");
        // Past deadlines are not "upcoming"...
        createJob(token, "Past Corp", "Engineer", "applied",
                "\"deadline\":\"" + LocalDate.now().minusDays(3) + "\"");
        // ...and neither are ones beyond the horizon.
        createJob(token, "Far Corp", "Engineer", "applied",
                "\"deadline\":\"" + LocalDate.now().plusDays(200) + "\"");
        createJob(token, "No Deadline Corp", "Engineer", "applied", null);

        mockMvc.perform(get("/api/analytics").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcomingDeadlines", hasSize(2)))
                .andExpect(jsonPath("$.upcomingDeadlines[0].companyName").value("Soon Corp"))
                .andExpect(jsonPath("$.upcomingDeadlines[1].companyName").value("Later Corp"));
    }

    @Test
    void activeInterviewsListJobsInScreeningOrInterview() throws Exception {
        String token = register();
        createJob(token, "Acme", "Engineer", "phone_screen", null);
        createJob(token, "Globex", "Engineer", "interview", null);
        createJob(token, "Initech", "Engineer", "applied", null);

        mockMvc.perform(get("/api/analytics").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeInterviews", hasSize(2)));
    }

    @Test
    void analyticsAreIsolatedBetweenUsers() throws Exception {
        String owner = register();
        long jobId = createJob(owner, "Acme", "Engineer", "wishlist", null);
        moveTo(owner, jobId, "applied");

        String other = register();
        mockMvc.perform(get("/api/analytics").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.total").value(0))
                .andExpect(jsonPath("$.companies", hasSize(0)))
                .andExpect(jsonPath("$.activeInterviews", hasSize(0)));
    }

    @Test
    void analyticsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/analytics")).andExpect(status().isUnauthorized());
    }
}

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

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The deterministic half of the assistant, which works with no AI provider configured.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String register() throws Exception {
        String email = "ai-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("AI Tester", email, "secret123"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private long createJobWithDescription(String token) throws Exception {
        String body = """
                {"companyName":"Acme","jobTitle":"Backend Engineer","status":"applied",
                 "description":"We need Java and Spring Boot experience. PostgreSQL, Kafka and Kubernetes are required."}
                """;
        MvcResult result = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void statusReportsThatGenerationIsUnavailableWithoutAKey() throws Exception {
        String token = register();

        mockMvc.perform(get("/api/ai/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.provider").value("none"))
                // The hint tells the user how to enable it rather than just failing.
                .andExpect(jsonPath("$.hint", containsString("AI_API_KEY")));
    }

    @Test
    void matchReturnsTransparentKeywordOverlapWithoutAnAiProvider() throws Exception {
        String token = register();
        long jobId = createJobWithDescription(token);

        mockMvc.perform(post("/api/ai/match")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":" + jobId + ",\"resumeText\":"
                                + "\"I build services in Java with Spring Boot and PostgreSQL.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Acme"))
                .andExpect(jsonPath("$.method").value("keyword-overlap"))
                // The result must be labelled honestly, not as an ATS score.
                .andExpect(jsonPath("$.disclaimer", containsString("not an ATS score")))
                .andExpect(jsonPath("$.jobSkills", hasItems("Java", "Spring Boot", "PostgreSQL", "Kafka", "Kubernetes")))
                .andExpect(jsonPath("$.matchedSkills", hasItems("Java", "Spring Boot", "PostgreSQL")))
                .andExpect(jsonPath("$.missingSkills", hasItems("Kafka", "Kubernetes")))
                .andExpect(jsonPath("$.skillCoverage").value(greaterThan(0)))
                .andExpect(jsonPath("$.suggestions", hasSize(greaterThan(0))))
                // Suggestions must never tell the candidate to embellish.
                .andExpect(jsonPath("$.suggestions[0]", containsString("If you have genuine experience")));
    }

    @Test
    void matchRefusesAJobWithNoDescription() throws Exception {
        String token = register();
        MvcResult job = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Acme\",\"jobTitle\":\"Engineer\",\"status\":\"applied\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long jobId = objectMapper.readTree(job.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/ai/match")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":" + jobId + ",\"resumeText\":\"Java and Spring Boot\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void matchRequiresResumeText() throws Exception {
        String token = register();
        long jobId = createJobWithDescription(token);

        mockMvc.perform(post("/api/ai/match")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":" + jobId + ",\"resumeText\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.resumeText").exists());
    }

    @Test
    void matchIsIsolatedBetweenUsers() throws Exception {
        String owner = register();
        long jobId = createJobWithDescription(owner);

        String other = register();
        mockMvc.perform(post("/api/ai/match")
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":" + jobId + ",\"resumeText\":\"Java\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void generateReturns503WithASetupHintInsteadOfInventedContent() throws Exception {
        String token = register();
        long jobId = createJobWithDescription(token);

        mockMvc.perform(post("/api/ai/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":" + jobId + ",\"task\":\"COVER_LETTER\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message", containsString("AI_API_KEY")));
    }

    @Test
    void aiEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/ai/status")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/ai/match").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}

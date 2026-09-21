package com.jobhunt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhunt.ai.AiProvider;
import com.jobhunt.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the provider-backed path with a stubbed provider, so the code that builds prompts
 * and returns generated text is covered without calling any real (or paid) service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiGenerateTest {

    @TestConfiguration
    static class StubAiConfiguration {
        @Bean
        @Primary
        RecordingAiProvider recordingAiProvider() {
            return new RecordingAiProvider();
        }
    }

    /** Records what it was asked, so the test can assert the prompt contents. */
    static class RecordingAiProvider implements AiProvider {
        final List<String> userPrompts = new ArrayList<>();
        String systemPrompt;

        @Override
        public String name() {
            return "stub";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            this.systemPrompt = systemPrompt;
            this.userPrompts.add(userPrompt);
            return "STUBBED CONTENT";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingAiProvider provider;

    private String register() throws Exception {
        String email = "aigen-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Gen Tester", email, "secret123"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private long createJob(String token) throws Exception {
        String body = """
                {"companyName":"Globex","jobTitle":"Platform Engineer","status":"applied","location":"Remote",
                 "description":"We need Kubernetes, Terraform and Go experience for our platform team."}
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
    void generateReturnsTheProvidersTextAndReportsTheProviderName() throws Exception {
        String token = register();
        long jobId = createJob(token);
        provider.userPrompts.clear();

        mockMvc.perform(post("/api/ai/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":" + jobId + ",\"task\":\"INTERVIEW_QUESTIONS\","
                                + "\"resumeText\":\"I have run Kubernetes clusters.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task").value("INTERVIEW_QUESTIONS"))
                .andExpect(jsonPath("$.provider").value("stub"))
                .andExpect(jsonPath("$.content").value("STUBBED CONTENT"));

        String prompt = provider.userPrompts.get(0);
        // Only real, user-owned data is sent to the provider.
        assertTrue(prompt.contains("Kubernetes, Terraform and Go"), "the job description must be included");
        assertTrue(prompt.contains("I have run Kubernetes clusters."), "the candidate's own text must be included");
        assertTrue(prompt.contains("Globex"), "the company name must be included");

        // The system prompt must forbid invention and ATS claims.
        assertTrue(provider.systemPrompt.contains("Never invent"), "the system prompt must forbid invention");
        assertTrue(provider.systemPrompt.contains("ATS"), "the system prompt must forbid ATS claims");
    }

    @Test
    void everyTaskProducesADistinctInstruction() throws Exception {
        String token = register();
        long jobId = createJob(token);
        provider.userPrompts.clear();

        for (String task : List.of("JOB_SUMMARY", "COVER_LETTER", "STAR_PRACTICE", "LEARNING_PLAN")) {
            mockMvc.perform(post("/api/ai/generate")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"jobId\":" + jobId + ",\"task\":\"" + task + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.task").value(task));
        }

        List<String> prompts = provider.userPrompts;
        assertFalse(prompts.get(0).equals(prompts.get(1)), "cover letter must differ from the summary prompt");
        assertTrue(prompts.get(0).contains("Summarise"), "job summary instruction");
        assertTrue(prompts.get(1).contains("cover letter"), "cover letter instruction");
        assertTrue(prompts.get(2).contains("STAR"), "STAR instruction");
        assertTrue(prompts.get(3).contains("learning"), "learning plan instruction");
    }

    @Test
    void anUnknownTaskIsRejected() throws Exception {
        String token = register();
        long jobId = createJob(token);

        mockMvc.perform(post("/api/ai/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":" + jobId + ",\"task\":\"WRITE_MY_ESSAY\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generateIsIsolatedBetweenUsers() throws Exception {
        String owner = register();
        long jobId = createJob(owner);

        String other = register();
        mockMvc.perform(post("/api/ai/generate")
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":" + jobId + ",\"task\":\"JOB_SUMMARY\"}"))
                .andExpect(status().isNotFound());
    }
}

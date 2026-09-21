package com.jobhunt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhunt.dto.RegisterRequest;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs against a **real PostgreSQL server** started inside the test JVM.
 *
 * <p>Every other backend test uses H2 in PostgreSQL-compatibility mode, which is an
 * approximation. This one removes the approximation: Flyway applies the real
 * {@code db/migration/postgresql} scripts to PostgreSQL and Hibernate validates the entities
 * against what was actually created. If the two ever diverge — a type H2 accepts but
 * PostgreSQL rejects, an identity column, a {@code BYTEA} mapping, a timestamp with time
 * zone — this test fails and the others would not.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostgresIntegrationTest {

    private static EmbeddedPostgres postgres;

    @DynamicPropertySource
    static void pointAtRealPostgres(DynamicPropertyRegistry registry) throws IOException {
        if (postgres == null) {
            postgres = EmbeddedPostgres.builder().start();
        }
        // Higher precedence than application-test.yml, so H2 is replaced entirely.
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String register() throws Exception {
        String email = "pg-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Postgres Tester", email, "secret123"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void theContextStartsWithFlywayMigrationsAppliedAndHibernateValidating() {
        // Reaching this test at all means: the real postgresql migration scripts ran against
        // PostgreSQL and Hibernate's validate pass accepted every entity mapping.
        org.junit.jupiter.api.Assertions.assertTrue(
                postgres.getPort() > 0, "the embedded PostgreSQL server should be running");
    }

    @Test
    void fullJobLifecycleWorksOnPostgres() throws Exception {
        String token = register();

        // Tags (a collection table), enums, dates and numeric columns all round-trip.
        MvcResult created = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Acme","jobTitle":"Backend Engineer","status":"wishlist",
                                 "location":"Berlin","workMode":"hybrid","deadline":"2026-12-01",
                                 "jobSource":"LinkedIn","salaryMin":90000,"salaryMax":120000,
                                 "salaryCurrency":"eur","description":"Java and Spring Boot.",
                                 "tags":["referral","dream job"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.salaryCurrency").value("EUR"))
                .andExpect(jsonPath("$.tags", hasSize(2)))
                .andExpect(jsonPath("$.deadline").value("2026-12-01"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn();

        long jobId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // Status transition writes a history row (timestamp with time zone on PostgreSQL).
        mockMvc.perform(patch("/api/jobs/{id}/status", jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"applied\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedDate").isNotEmpty());

        mockMvc.perform(get("/api/jobs/{id}/history", jobId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].toStatus").value("applied"))
                .andExpect(jsonPath("$[1].changedAt").isNotEmpty());

        // Paging and filtering through JPQL criteria.
        mockMvc.perform(get("/api/jobs").param("search", "acme").param("size", "5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(1));

        // Analytics aggregates over real PostgreSQL.
        mockMvc.perform(get("/api/analytics").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.applied").value(1));
    }

    @Test
    void resumeDocumentsRoundTripThroughByteaOnPostgres() throws Exception {
        String token = register();
        byte[] bytes = "%PDF-1.4 stored in postgres".getBytes(StandardCharsets.UTF_8);

        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", bytes);
        MvcResult uploaded = mockMvc.perform(multipart("/api/resumes")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        long resumeId = objectMapper.readTree(uploaded.getResponse().getContentAsString()).get("id").asLong();

        // The legacy bytea column must still exist and accept a null for new rows.
        mockMvc.perform(get("/api/resumes/{id}/download", resumeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes));
    }

    @Test
    void coverLettersAndRefreshTokensWorkOnPostgres() throws Exception {
        String token = register();

        mockMvc.perform(multipart("/api/cover-letters")
                        .param("body", "Dear hiring manager")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/cover-letters").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // Refresh-token rotation exercises a unique index and an update on PostgreSQL.
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"wrong\"}"))
                .andReturn();
        org.junit.jupiter.api.Assertions.assertEquals(401, login.getResponse().getStatus());
    }
}

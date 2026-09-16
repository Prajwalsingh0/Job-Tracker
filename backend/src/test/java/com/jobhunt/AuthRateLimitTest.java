package com.jobhunt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhunt.dto.LoginRequest;
import com.jobhunt.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Brute-force protection is configured with a low threshold here (rather than at runtime)
 * so the behaviour is exercised without hammering the endpoint.
 */
@SpringBootTest(properties = {
        "app.security.login-rate-limit.max-attempts=3",
        "app.security.login-rate-limit.window-seconds=300"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void repeatedFailedLoginsAreRateLimited() throws Exception {
        String email = "ratelimit-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Rate Limit", email, "secret123"))))
                .andExpect(status().isCreated());

        String wrongPassword = objectMapper.writeValueAsString(new LoginRequest(email, "not-the-password"));

        // The first three failures are answered normally.
        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(wrongPassword))
                    .andExpect(status().isUnauthorized());
        }

        // The fourth is blocked before the credentials are even checked.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongPassword))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));

        // Even the correct password is refused while the window is open.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "secret123"))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void rateLimitingDoesNotBlockNormalAuthenticatedTraffic() throws Exception {
        String email = "noratelimit-" + UUID.randomUUID() + "@example.com";
        var registered = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Normal User", email, "secret123"))))
                .andExpect(status().isCreated())
                .andReturn();

        String token = objectMapper.readTree(registered.getResponse().getContentAsString()).get("token").asText();

        // Repeated reads of a normal endpoint are unaffected by the auth limiter.
        for (int i = 0; i < 8; i++) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/api/jobs")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }
}

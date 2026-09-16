package com.jobhunt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhunt.dto.LoginRequest;
import com.jobhunt.dto.RegisterRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Refresh-token lifecycle, cookie hardening, response headers and upload signature checks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityTest {

    private static final String REFRESH_COOKIE = "jobhunt_refresh";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String email;
    private String password = "secret123";

    private void registerUser() throws Exception {
        email = "security-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Security Tester", email, password))))
                .andExpect(status().isCreated());
    }

    private Cookie loginAndGetRefreshCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie(REFRESH_COOKIE);
    }

    @Test
    void loginIssuesAnHttpOnlyRefreshCookieAndAnAccessTokenInTheBody() throws Exception {
        registerUser();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE);
        assertNotNull(cookie, "refresh cookie must be set");
        assertTrue(cookie.isHttpOnly(), "refresh cookie must be httpOnly so scripts cannot read it");
        // The refresh token itself must never appear in the response body.
        String body = result.getResponse().getContentAsString();
        assertTrue(body.indexOf(cookie.getValue()) < 0, "refresh token leaked into the response body");
    }

    @Test
    void refreshRotatesTheCookieAndIssuesANewAccessToken() throws Exception {
        registerUser();
        Cookie original = loginAndGetRefreshCookie();

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh").cookie(original))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        Cookie rotated = refreshed.getResponse().getCookie(REFRESH_COOKIE);
        assertNotNull(rotated, "rotation must issue a new cookie");
        assertNotEquals(original.getValue(), rotated.getValue(), "the refresh token must change on use");
    }

    @Test
    void replayingARotatedRefreshTokenIsRejected() throws Exception {
        registerUser();
        Cookie original = loginAndGetRefreshCookie();

        mockMvc.perform(post("/api/auth/refresh").cookie(original)).andExpect(status().isOk());

        // The old token was rotated, so presenting it again must fail.
        mockMvc.perform(post("/api/auth/refresh").cookie(original))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutACookieIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutClearsTheCookieAndRevokesTheRefreshToken() throws Exception {
        registerUser();
        Cookie cookie = loginAndGetRefreshCookie();

        MvcResult logout = mockMvc.perform(post("/api/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie cleared = logout.getResponse().getCookie(REFRESH_COOKIE);
        assertNotNull(cleared, "logout must clear the cookie");
        assertTrue(cleared.getMaxAge() == 0, "logout must expire the cookie");

        // The revoked token can no longer be exchanged.
        mockMvc.perform(post("/api/auth/refresh").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mockMvc.perform(get("/api/jobs"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void resumeUploadRejectsAFileWhoseBytesDoNotMatchItsClaimedType() throws Exception {
        registerUser();
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString()).get("token").asText();

        // Claims to be a PDF, but the bytes are not.
        MockMultipartFile disguised = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "this is definitely not a PDF".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/resumes")
                        .file(disguised)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        // A real PDF header is accepted.
        MockMultipartFile genuine = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "%PDF-1.4 real enough".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/resumes")
                        .file(genuine)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }
}

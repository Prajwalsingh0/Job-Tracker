package com.jobhunt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhunt.dto.RegisterRequest;
import com.jobhunt.entity.CoverLetter;
import com.jobhunt.repository.CoverLetterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cover letters: pasted text, uploaded documents, ownership and storage cleanup.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoverLetterApiTest {

    private static final byte[] PDF_BYTES = "%PDF-1.4 cover letter".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CoverLetterRepository coverLetterRepository;

    @Value("${app.storage.root}")
    private String storageRoot;

    private String register() throws Exception {
        String email = "cover-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Cover Tester", email, "secret123"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private long createTextLetter(String token, String body) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/cover-letters")
                        .param("body", body)
                        .param("name", "Pasted letter")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void aTextOnlyLetterCanBeCreatedAndListed() throws Exception {
        String token = register();

        mockMvc.perform(multipart("/api/cover-letters")
                        .param("body", "Dear hiring manager, I am applying because...")
                        .param("versionTag", "Draft 1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cover letter"))
                .andExpect(jsonPath("$.body").isNotEmpty())
                .andExpect(jsonPath("$.versionTag").value("Draft 1"))
                // No document was uploaded, so these stay absent.
                .andExpect(jsonPath("$.fileName").doesNotExist())
                .andExpect(jsonPath("$.fileSize").doesNotExist());

        mockMvc.perform(get("/api/cover-letters").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void aDocumentLetterIsStoredOnDiskAndNeverInTheDatabase() throws Exception {
        String token = register();

        MockMultipartFile file = new MockMultipartFile("file", "letter.pdf", "application/pdf", PDF_BYTES);
        MvcResult result = mockMvc.perform(multipart("/api/cover-letters")
                        .file(file)
                        .param("jobId", "")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("letter.pdf"))
                .andExpect(jsonPath("$.name").value("letter"))
                .andReturn();

        long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        CoverLetter stored = coverLetterRepository.findById(id).orElseThrow();
        assertNotNull(stored.getStorageKey(), "a storage key must be recorded");

        Path onDisk = Paths.get(storageRoot).resolve(stored.getStorageKey());
        assertTrue(Files.isRegularFile(onDisk), "the letter must exist on disk");
        assertFalse(result.getResponse().getContentAsString().contains(stored.getStorageKey()),
                "the storage key must not leak through the API");
    }

    @Test
    void aLetterCanBeBothTextAndDocument() throws Exception {
        String token = register();

        MockMultipartFile file = new MockMultipartFile("file", "letter.pdf", "application/pdf", PDF_BYTES);
        mockMvc.perform(multipart("/api/cover-letters")
                        .file(file)
                        .param("body", "Short note alongside the attachment")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Short note alongside the attachment"))
                .andExpect(jsonPath("$.fileName").value("letter.pdf"));
    }

    @Test
    void aLetterNeedsEitherTextOrADocument() throws Exception {
        String token = register();

        mockMvc.perform(multipart("/api/cover-letters").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        // Whitespace does not count as text.
        mockMvc.perform(multipart("/api/cover-letters")
                        .param("body", "   ")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oversizedTextIsRejected() throws Exception {
        String token = register();

        mockMvc.perform(multipart("/api/cover-letters")
                        .param("body", "x".repeat(20_001))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aDisguisedFileIsRejected() throws Exception {
        String token = register();

        MockMultipartFile disguised = new MockMultipartFile(
                "file", "letter.pdf", "application/pdf", "definitely not a PDF".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/cover-letters")
                        .file(disguised)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadStreamsTheStoredDocument() throws Exception {
        String token = register();

        MockMultipartFile file = new MockMultipartFile("file", "letter.pdf", "application/pdf", PDF_BYTES);
        MvcResult created = mockMvc.perform(multipart("/api/cover-letters")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/cover-letters/{id}/download", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PDF_BYTES));

        // A text-only letter has nothing to download.
        long textId = createTextLetter(token, "No attachment here");
        mockMvc.perform(get("/api/cover-letters/{id}/download", textId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesTheRowAndTheStoredFile() throws Exception {
        String token = register();

        MockMultipartFile file = new MockMultipartFile("file", "letter.pdf", "application/pdf", PDF_BYTES);
        MvcResult created = mockMvc.perform(multipart("/api/cover-letters")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        CoverLetter stored = coverLetterRepository.findById(id).orElseThrow();
        Path onDisk = Paths.get(storageRoot).resolve(stored.getStorageKey());
        assertTrue(Files.exists(onDisk));

        mockMvc.perform(delete("/api/cover-letters/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertTrue(coverLetterRepository.findById(id).isEmpty());
        assertFalse(Files.exists(onDisk), "the stored document must be removed too");
    }

    @Test
    void lettersAreIsolatedBetweenUsers() throws Exception {
        String owner = register();
        long id = createTextLetter(owner, "Private letter");

        String other = register();
        mockMvc.perform(get("/api/cover-letters").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(delete("/api/cover-letters/{id}", id).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    @Test
    void aLetterCanBeLinkedToAJobOfTheSameUserOnly() throws Exception {
        String token = register();

        MvcResult job = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Acme\",\"jobTitle\":\"Engineer\",\"status\":\"applied\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long jobId = objectMapper.readTree(job.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(multipart("/api/cover-letters")
                        .param("body", "Written for the Acme role")
                        .param("jobId", String.valueOf(jobId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").value(jobId));

        // Another user cannot attach a letter to someone else's job.
        String other = register();
        mockMvc.perform(multipart("/api/cover-letters")
                        .param("body", "Trying to attach to a foreign job")
                        .param("jobId", String.valueOf(jobId))
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }
}

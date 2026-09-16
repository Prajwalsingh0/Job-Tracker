package com.jobhunt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhunt.dto.RegisterRequest;
import com.jobhunt.entity.Resume;
import com.jobhunt.repository.ResumeRepository;
import com.jobhunt.storage.LocalFileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Documents live in file storage, not the database. Every test registers its own user so
 * the assertions about "my resumes" stay unambiguous.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResumeStorageTest {

    private static final byte[] PDF_BYTES = "%PDF-1.4 stored on disk".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ResumeRepository resumeRepository;

    @Value("${app.storage.root}")
    private String storageRoot;

    private String registerAndLogin() throws Exception {
        String email = "storage-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Storage Tester", email, "secret123"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private long uploadResume(String token) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "stored.pdf", "application/pdf", PDF_BYTES);
        MvcResult result = mockMvc.perform(multipart("/api/resumes")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void uploadWritesTheDocumentToStorageAndKeepsTheDatabaseFree() throws Exception {
        String token = registerAndLogin();
        long id = uploadResume(token);

        Resume stored = resumeRepository.findById(id).orElseThrow();
        assertNotNull(stored.getStorageKey(), "a storage key must be recorded");
        assertTrue(stored.getFileSize() > 0, "the size must be recorded");
        assertNull(stored.getFileData(), "the database must not hold the document bytes");

        Path onDisk = Paths.get(storageRoot).resolve(stored.getStorageKey());
        assertTrue(Files.isRegularFile(onDisk), "the document must exist on disk at " + onDisk);
        assertEquals(PDF_BYTES.length, Files.size(onDisk));
    }

    @Test
    void theStorageKeyNeverLeaksThroughTheApi() throws Exception {
        String token = registerAndLogin();
        uploadResume(token);

        mockMvc.perform(get("/api/resumes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").isNotEmpty())
                .andExpect(jsonPath("$[0].storageKey").doesNotExist())
                .andExpect(jsonPath("$[0].fileData").doesNotExist());
    }

    @Test
    void downloadStreamsTheExactStoredBytes() throws Exception {
        String token = registerAndLogin();
        long id = uploadResume(token);

        mockMvc.perform(get("/api/resumes/{id}/download", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PDF_BYTES))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void deletingAResumeRemovesTheStoredFile() throws Exception {
        String token = registerAndLogin();
        long id = uploadResume(token);

        Resume stored = resumeRepository.findById(id).orElseThrow();
        Path onDisk = Paths.get(storageRoot).resolve(stored.getStorageKey());
        assertTrue(Files.exists(onDisk), "precondition: the document is on disk");

        mockMvc.perform(delete("/api/resumes/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertFalse(Files.exists(onDisk), "the document must be removed from storage as well");
        assertTrue(resumeRepository.findById(id).isEmpty());
    }

    @Test
    void anotherUserCannotDownloadSomeoneElsesDocument() throws Exception {
        String owner = registerAndLogin();
        long id = uploadResume(owner);

        String other = registerAndLogin();
        mockMvc.perform(get("/api/resumes/{id}/download", id).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    @Test
    void storageRefusesKeysThatEscapeTheRoot(@TempDir Path tempDir) throws Exception {
        LocalFileStorageService service = new LocalFileStorageService(tempDir.toString());

        assertThrows(IllegalArgumentException.class, () -> service.loadAsResource("../../etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> service.loadAsResource("resumes/../../escape.pdf"));
        assertThrows(IllegalArgumentException.class, () -> service.loadAsResource(""));
        assertThrows(IllegalArgumentException.class, () -> service.loadAsResource(null));
    }
}

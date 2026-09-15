package com.jobhunt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhunt.dto.RegisterRequest;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Job CRUD, paging/sorting/search, CSV export, duplicate detection, status transitions,
 * dashboard statistics, resume handling and per-user data isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobApiTest {

    private static final String BEARER = "Bearer ";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerUser(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Test User", email, "secret123"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private long createJob(String token, String company, String title, String status, String location)
            throws Exception {
        // Built with Jackson so values containing quotes or commas stay valid JSON.
        var payload = new java.util.LinkedHashMap<String, String>();
        payload.put("companyName", company);
        payload.put("jobTitle", title);
        payload.put("status", status);
        payload.put("location", location);

        MvcResult result = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", BEARER + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    // ------------------------------------------------------------------ CRUD

    @Test
    void jobLifecycleCreateReadUpdateStatusDelete() throws Exception {
        String token = registerUser("lifecycle");
        long id = createJob(token, "Acme Corp", "Backend Engineer", "wishlist", "Berlin");

        // A wishlist entry has no application date yet.
        mockMvc.perform(get("/api/jobs/{id}", id).header("Authorization", BEARER + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Acme Corp"))
                .andExpect(jsonPath("$.status").value("wishlist"))
                .andExpect(jsonPath("$.appliedDate").doesNotExist());

        // Moving to "applied" stamps the application date.
        mockMvc.perform(patch("/api/jobs/{id}/status", id)
                        .header("Authorization", BEARER + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"applied\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("applied"))
                .andExpect(jsonPath("$.appliedDate").isNotEmpty());

        // Full update.
        mockMvc.perform(put("/api/jobs/{id}", id)
                        .header("Authorization", BEARER + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Acme Corp\",\"jobTitle\":\"Senior Backend Engineer\","
                                + "\"status\":\"interview\",\"salaryRange\":\"$120k - $150k\","
                                + "\"notes\":\"Referred by a friend\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobTitle").value("Senior Backend Engineer"))
                .andExpect(jsonPath("$.status").value("interview"))
                .andExpect(jsonPath("$.salaryRange").value("$120k - $150k"));

        mockMvc.perform(get("/api/jobs").header("Authorization", BEARER + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(delete("/api/jobs/{id}", id).header("Authorization", BEARER + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/jobs/{id}", id).header("Authorization", BEARER + token))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------- search, sort, paging

    @Test
    void searchAndStatusFilterNarrowTheResultSet() throws Exception {
        String token = registerUser("search");
        createJob(token, "Globex", "Data Analyst", "applied", "Remote");
        createJob(token, "Initech", "Platform Engineer", "applied", "Austin");
        createJob(token, "Globex", "Product Manager", "offer", "Remote");

        mockMvc.perform(get("/api/jobs").param("search", "globex").header("Authorization", BEARER + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));

        mockMvc.perform(get("/api/jobs").param("search", "remote").header("Authorization", BEARER + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));

        mockMvc.perform(get("/api/jobs").param("status", "applied").header("Authorization", BEARER + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));

        mockMvc.perform(get("/api/jobs").param("status", "offer").header("Authorization", BEARER + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].companyName").value("Globex"));

        mockMvc.perform(get("/api/jobs").param("status", "not_a_status").header("Authorization", BEARER + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listIsPagedAndReportsPageMetadata() throws Exception {
        String token = registerUser("paging");
        createJob(token, "Alpha", "Engineer", "applied", "Remote");
        createJob(token, "Bravo", "Engineer", "applied", "Remote");
        createJob(token, "Charlie", "Engineer", "applied", "Remote");

        mockMvc.perform(get("/api/jobs")
                        .param("size", "2")
                        .param("sort", "companyName")
                        .param("direction", "asc")
                        .header("Authorization", BEARER + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content[0].companyName").value("Alpha"));

        mockMvc.perform(get("/api/jobs")
                        .param("page", "1")
                        .param("size", "2")
                        .param("sort", "companyName")
                        .param("direction", "asc")
                        .header("Authorization", BEARER + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content[0].companyName").value("Charlie"));
    }

    @Test
    void sortDirectionIsHonoured() throws Exception {
        String token = registerUser("sorting");
        createJob(token, "Alpha", "Engineer", "applied", "Remote");
        createJob(token, "Zulu", "Engineer", "applied", "Remote");

        mockMvc.perform(get("/api/jobs")
                        .param("sort", "companyName")
                        .param("direction", "asc")
                        .header("Authorization", BEARER + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].companyName").value("Alpha"));

        mockMvc.perform(get("/api/jobs")
                        .param("sort", "companyName")
                        .param("direction", "desc")
                        .header("Authorization", BEARER + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].companyName").value("Zulu"));
    }

    @Test
    void unknownSortFieldFallsBackInsteadOfFailing() throws Exception {
        String token = registerUser("badsort");
        createJob(token, "Alpha", "Engineer", "applied", "Remote");

        mockMvc.perform(get("/api/jobs")
                        .param("sort", "passwordHash")
                        .param("direction", "asc")
                        .header("Authorization", BEARER + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    // -------------------------------------------------------------- CSV export

    @Test
    void csvExportReturnsHeaderAndRows() throws Exception {
        String token = registerUser("csv");
        createJob(token, "Acme, Inc", "Engineer \"Senior\"", "applied", "Remote");

        mockMvc.perform(get("/api/jobs/export").header("Authorization", BEARER + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().string(containsString("id,companyName,jobTitle,status")))
                // A company name containing a comma must be quoted, not split into two columns.
                .andExpect(content().string(containsString("\"Acme, Inc\"")))
                // Embedded quotes are doubled per RFC 4180.
                .andExpect(content().string(containsString("\"Engineer \"\"Senior\"\"\"")));
    }

    @Test
    void csvExportHonoursTheStatusFilter() throws Exception {
        String token = registerUser("csvfilter");
        createJob(token, "Alpha", "Engineer", "applied", "Remote");
        createJob(token, "Bravo", "Manager", "wishlist", "Remote");

        mockMvc.perform(get("/api/jobs/export").param("status", "applied")
                        .header("Authorization", BEARER + token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Alpha")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Bravo"))));
    }

    // ------------------------------------------------------ duplicate guard

    @Test
    void duplicateCompanyAndTitleIsRejected() throws Exception {
        String token = registerUser("dupe");
        createJob(token, "Acme Corp", "Backend Engineer", "applied", "Berlin");

        mockMvc.perform(post("/api/jobs")
                        .header("Authorization", BEARER + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"acme corp\",\"jobTitle\":\"backend engineer\"}"))
                .andExpect(status().isConflict());

        // A different title at the same company is fine.
        createJob(token, "Acme Corp", "Data Engineer", "applied", "Berlin");
    }

    @Test
    void editingAJobDoesNotTripItsOwnDuplicateGuard() throws Exception {
        String token = registerUser("selfdupe");
        long id = createJob(token, "Acme Corp", "Backend Engineer", "applied", "Berlin");

        mockMvc.perform(put("/api/jobs/{id}", id)
                        .header("Authorization", BEARER + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Acme Corp\",\"jobTitle\":\"Backend Engineer\","
                                + "\"status\":\"interview\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("interview"));
    }

    // ------------------------------------------------------------- statistics

    @Test
    void dashboardStatisticsMatchJobStatuses() throws Exception {
        String token = registerUser("stats");
        createJob(token, "One", "Wishlist role", "wishlist", "Remote");
        createJob(token, "Two", "Applied role", "applied", "Remote");
        createJob(token, "Three", "Screening role", "interview", "Remote");
        createJob(token, "Four", "Offer role", "offer", "Remote");
        createJob(token, "Five", "Rejected role", "rejected", "Remote");

        mockMvc.perform(get("/api/jobs/stats").header("Authorization", BEARER + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.wishlist").value(1))
                .andExpect(jsonPath("$.applied").value(4))
                .andExpect(jsonPath("$.interviewing").value(1))
                .andExpect(jsonPath("$.offers").value(1))
                .andExpect(jsonPath("$.rejected").value(1))
                // 3 of the 4 applications progressed past "applied"
                .andExpect(jsonPath("$.responseRate").value(75.0))
                .andExpect(jsonPath("$.interviewRate").value(25.0));
    }

    // -------------------------------------------------------------- isolation

    @Test
    void jobsAreIsolatedBetweenUsers() throws Exception {
        String alice = registerUser("alice");
        String bob = registerUser("bob");

        long aliceJobId = createJob(alice, "Alice Corp", "Secret Role", "applied", "Remote");

        mockMvc.perform(get("/api/jobs").header("Authorization", BEARER + bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/jobs/{id}", aliceJobId).header("Authorization", BEARER + bob))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/jobs/{id}", aliceJobId).header("Authorization", BEARER + bob))
                .andExpect(status().isNotFound());

        // Alice can still see her own job.
        mockMvc.perform(get("/api/jobs/{id}", aliceJobId).header("Authorization", BEARER + alice))
                .andExpect(status().isOk());

        // Bob's CSV export cannot contain Alice's data.
        mockMvc.perform(get("/api/jobs/export").header("Authorization", BEARER + bob))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Secret Role"))));
    }

    @Test
    void jobValidationRejectsMissingRequiredFields() throws Exception {
        String token = registerUser("validation");

        mockMvc.perform(post("/api/jobs")
                        .header("Authorization", BEARER + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"\",\"jobTitle\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.companyName").exists())
                .andExpect(jsonPath("$.fieldErrors.jobTitle").exists());
    }

    // ----------------------------------------------------------------- resumes

    @Test
    void resumeCanBeUploadedListedDownloadedAndAttachedToAJob() throws Exception {
        String token = registerUser("resume");
        byte[] bytes = "%PDF-1.4 minimal test document".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "my-resume.pdf", "application/pdf", bytes);

        MvcResult uploaded = mockMvc.perform(multipart("/api/resumes")
                        .file(file)
                        .param("versionTag", "Backend v1")
                        .header("Authorization", BEARER + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("my-resume.pdf"))
                .andExpect(jsonPath("$.fileType").value("pdf"))
                .andExpect(jsonPath("$.versionTag").value("Backend v1"))
                .andReturn();

        JsonNode node = objectMapper.readTree(uploaded.getResponse().getContentAsString());
        long resumeId = node.get("id").asLong();

        mockMvc.perform(get("/api/resumes").header("Authorization", BEARER + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].usageCount").value(0));

        // Attach the resume to a new job.
        mockMvc.perform(post("/api/jobs")
                        .header("Authorization", BEARER + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Acme\",\"jobTitle\":\"Engineer\",\"status\":\"applied\","
                                + "\"resumeId\":" + resumeId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resumeId").value(resumeId));

        // usageCount now reflects the attachment.
        mockMvc.perform(get("/api/resumes").header("Authorization", BEARER + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].usageCount").value(1));

        mockMvc.perform(get("/api/resumes/{id}/download", resumeId).header("Authorization", BEARER + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(content().bytes(bytes));

        // Deleting the resume keeps the job but detaches the document.
        mockMvc.perform(delete("/api/resumes/{id}", resumeId).header("Authorization", BEARER + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/jobs").header("Authorization", BEARER + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].resumeId").doesNotExist());
    }

    @Test
    void resumeUploadRejectsUnsupportedFileType() throws Exception {
        String token = registerUser("badfile");
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/resumes").file(file).header("Authorization", BEARER + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void attachingAnotherUsersResumeIsRejected() throws Exception {
        String alice = registerUser("resume-owner");
        String bob = registerUser("resume-thief");

        MockMultipartFile file = new MockMultipartFile(
                "file", "alice.pdf", "application/pdf", "%PDF-1.4".getBytes(StandardCharsets.UTF_8));

        MvcResult uploaded = mockMvc.perform(multipart("/api/resumes")
                        .file(file)
                        .header("Authorization", BEARER + alice))
                .andExpect(status().isCreated())
                .andReturn();
        long resumeId = objectMapper.readTree(uploaded.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/jobs")
                        .header("Authorization", BEARER + bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Acme\",\"jobTitle\":\"Engineer\",\"resumeId\":" + resumeId + "}"))
                .andExpect(status().isNotFound());
    }
}

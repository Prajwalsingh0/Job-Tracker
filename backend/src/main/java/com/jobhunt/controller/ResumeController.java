package com.jobhunt.controller;

import com.jobhunt.dto.ResumeDto;
import com.jobhunt.entity.Resume;
import com.jobhunt.entity.ResumeFileType;
import com.jobhunt.security.UserPrincipal;
import com.jobhunt.service.ResumeService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @GetMapping
    public List<ResumeDto> list(@AuthenticationPrincipal UserPrincipal principal) {
        return resumeService.list(principal.getId());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeDto upload(@AuthenticationPrincipal UserPrincipal principal,
                            @RequestParam("file") MultipartFile file,
                            @RequestParam(required = false) String name,
                            @RequestParam(required = false) String versionTag) {
        return resumeService.upload(principal.getId(), file, name, versionTag);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable Long id) {
        ResumeService.ResumeDocument document = resumeService.download(principal.getId(), id);
        Resume resume = document.resume();

        MediaType mediaType = resume.getFileType() == ResumeFileType.PDF
                ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType(DOCX_CONTENT_TYPE);

        String disposition = ContentDisposition.attachment()
                .filename(resume.getFileName(), StandardCharsets.UTF_8)
                .build()
                .toString();

        // Streams from storage rather than buffering the document in memory.
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(mediaType)
                .contentLength(resume.getFileSize())
                .body(document.resource());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        resumeService.delete(principal.getId(), id);
    }
}

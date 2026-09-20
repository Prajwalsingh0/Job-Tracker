package com.jobhunt.controller;

import com.jobhunt.dto.CoverLetterDto;
import com.jobhunt.entity.CoverLetter;
import com.jobhunt.entity.ResumeFileType;
import com.jobhunt.security.UserPrincipal;
import com.jobhunt.service.CoverLetterService;
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
@RequestMapping("/api/cover-letters")
public class CoverLetterController {

    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final CoverLetterService coverLetterService;

    public CoverLetterController(CoverLetterService coverLetterService) {
        this.coverLetterService = coverLetterService;
    }

    @GetMapping
    public List<CoverLetterDto> list(@AuthenticationPrincipal UserPrincipal principal) {
        return coverLetterService.list(principal.getId());
    }

    /**
     * Creates a cover letter from a document, pasted text, or both. Everything is optional
     * except having at least one of {@code file} or {@code body}.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CoverLetterDto create(@AuthenticationPrincipal UserPrincipal principal,
                                 @RequestParam(value = "file", required = false) MultipartFile file,
                                 @RequestParam(required = false) String body,
                                 @RequestParam(required = false) String name,
                                 @RequestParam(required = false) String versionTag,
                                 @RequestParam(required = false) Long jobId) {
        return coverLetterService.create(principal.getId(), file, body, name, versionTag, jobId);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable Long id) {
        CoverLetterService.CoverLetterDocument document = coverLetterService.download(principal.getId(), id);
        CoverLetter letter = document.coverLetter();

        MediaType mediaType = letter.getFileType() == ResumeFileType.DOCX
                ? MediaType.parseMediaType(DOCX_CONTENT_TYPE)
                : MediaType.APPLICATION_PDF;

        String disposition = ContentDisposition.attachment()
                .filename(letter.getFileName(), StandardCharsets.UTF_8)
                .build()
                .toString();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(mediaType)
                .contentLength(letter.getFileSize() == null ? 0L : letter.getFileSize())
                .body(document.resource());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        coverLetterService.delete(principal.getId(), id);
    }
}

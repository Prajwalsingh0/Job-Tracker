package com.jobhunt.controller;

import com.jobhunt.dto.JobDto;
import com.jobhunt.dto.JobRequest;
import com.jobhunt.dto.JobStatsDto;
import com.jobhunt.dto.JobStatusHistoryDto;
import com.jobhunt.dto.JobStatusUpdateRequest;
import com.jobhunt.dto.PageResponse;
import com.jobhunt.entity.JobStatus;
import com.jobhunt.security.UserPrincipal;
import com.jobhunt.service.JobService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * Paged job list. Optional {@code search} and {@code status} filters are applied in the
     * database, along with {@code sort}/{@code direction} and {@code page}/{@code size}.
     */
    @GetMapping
    public PageResponse<JobDto> list(@AuthenticationPrincipal UserPrincipal principal,
                                     @RequestParam(required = false) String search,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @RequestParam(required = false) String sort,
                                     @RequestParam(defaultValue = "desc") String direction) {
        return jobService.list(principal.getId(), search, parseStatus(status), page, size, sort, direction);
    }

    @GetMapping("/stats")
    public JobStatsDto stats(@AuthenticationPrincipal UserPrincipal principal) {
        return jobService.stats(principal.getId());
    }

    /** CSV export of the caller's jobs, honouring the same search/status filters. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal UserPrincipal principal,
                                         @RequestParam(required = false) String search,
                                         @RequestParam(required = false) String status) {
        byte[] body = jobService.exportCsv(principal.getId(), search, parseStatus(status));

        String fileName = "job-applications-" + LocalDate.now() + ".csv";
        String disposition = ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build()
                .toString();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .contentLength(body.length)
                .body(body);
    }

    @GetMapping("/activity")
    public List<JobStatusHistoryDto> activity(@AuthenticationPrincipal UserPrincipal principal) {
        return jobService.recentActivity(principal.getId());
    }

    @GetMapping("/{id}/history")
    public List<JobStatusHistoryDto> history(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable Long id) {
        return jobService.history(principal.getId(), id);
    }

    @GetMapping("/{id}")
    public JobDto get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return jobService.get(principal.getId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobDto create(@AuthenticationPrincipal UserPrincipal principal,
                         @Valid @RequestBody JobRequest request) {
        return jobService.create(principal.getId(), request);
    }

    @PutMapping("/{id}")
    public JobDto update(@AuthenticationPrincipal UserPrincipal principal,
                         @PathVariable Long id,
                         @Valid @RequestBody JobRequest request) {
        return jobService.update(principal.getId(), id, request);
    }

    @PatchMapping("/{id}/status")
    public JobDto updateStatus(@AuthenticationPrincipal UserPrincipal principal,
                               @PathVariable Long id,
                               @Valid @RequestBody JobStatusUpdateRequest request) {
        return jobService.updateStatus(principal.getId(), id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        jobService.delete(principal.getId(), id);
    }

    private JobStatus parseStatus(String status) {
        return StringUtils.hasText(status) ? JobStatus.fromValue(status) : null;
    }
}

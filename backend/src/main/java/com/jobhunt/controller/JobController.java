package com.jobhunt.controller;

import com.jobhunt.dto.JobDto;
import com.jobhunt.dto.JobRequest;
import com.jobhunt.dto.JobStatsDto;
import com.jobhunt.dto.JobStatusUpdateRequest;
import com.jobhunt.entity.JobStatus;
import com.jobhunt.security.UserPrincipal;
import com.jobhunt.service.JobService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    /** Optional {@code search} and {@code status} query parameters filter the result set. */
    @GetMapping
    public List<JobDto> list(@AuthenticationPrincipal UserPrincipal principal,
                             @RequestParam(required = false) String search,
                             @RequestParam(required = false) String status) {
        JobStatus parsedStatus = StringUtils.hasText(status) ? JobStatus.fromValue(status) : null;
        return jobService.list(principal.getId(), search, parsedStatus);
    }

    @GetMapping("/stats")
    public JobStatsDto stats(@AuthenticationPrincipal UserPrincipal principal) {
        return jobService.stats(principal.getId());
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
}

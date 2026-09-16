package com.jobhunt.dto;

import com.jobhunt.entity.JobStatus;

import java.time.Instant;

/** One recorded pipeline transition, oldest first. */
public record JobStatusHistoryDto(
        Long id,
        Long jobId,
        String jobCompanyName,
        String jobTitle,
        JobStatus fromStatus,
        JobStatus toStatus,
        Instant changedAt,
        String note
) {
}

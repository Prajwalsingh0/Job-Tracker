package com.jobhunt.dto;

import com.jobhunt.entity.JobOutcome;
import com.jobhunt.entity.JobStatus;
import com.jobhunt.entity.OutcomeReason;

import java.time.Instant;
import java.time.LocalDate;

public record JobDto(
        Long id,
        String companyName,
        String jobTitle,
        String jobUrl,
        String description,
        String location,
        String salaryRange,
        JobStatus status,
        LocalDate appliedDate,
        LocalDate targetApplyDate,
        JobOutcome outcome,
        OutcomeReason outcomeReason,
        String feedback,
        String notes,
        Long resumeId,
        Instant createdAt,
        Instant updatedAt
) {
}

package com.jobhunt.dto;

import com.jobhunt.entity.JobOutcome;
import com.jobhunt.entity.JobStatus;
import com.jobhunt.entity.OutcomeReason;
import com.jobhunt.entity.WorkMode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
        String jobSource,
        WorkMode workMode,
        LocalDate deadline,
        Long salaryMin,
        Long salaryMax,
        String salaryCurrency,
        /** Sorted so the JSON is stable between requests. */
        List<String> tags,
        Long resumeId,
        Instant createdAt,
        Instant updatedAt
) {
}

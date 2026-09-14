package com.jobhunt.dto;

import com.jobhunt.entity.JobStatus;
import com.jobhunt.entity.OutcomeReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Create/update payload for a job. {@code outcome} is intentionally not accepted here:
 * it is derived from {@code status} by the service so the two can never disagree.
 */
public record JobRequest(
        @NotBlank(message = "Company name is required")
        @Size(max = 150, message = "Company name must be at most 150 characters")
        String companyName,

        @NotBlank(message = "Job title is required")
        @Size(max = 150, message = "Job title must be at most 150 characters")
        String jobTitle,

        @Size(max = 1000, message = "Job URL must be at most 1000 characters")
        String jobUrl,

        @Size(max = 20000, message = "Description must be at most 20000 characters")
        String description,

        @Size(max = 200, message = "Location must be at most 200 characters")
        String location,

        @Size(max = 100, message = "Salary range must be at most 100 characters")
        String salaryRange,

        JobStatus status,

        LocalDate appliedDate,

        LocalDate targetApplyDate,

        OutcomeReason outcomeReason,

        @Size(max = 5000, message = "Feedback must be at most 5000 characters")
        String feedback,

        @Size(max = 10000, message = "Notes must be at most 10000 characters")
        String notes,

        Long resumeId
) {
}

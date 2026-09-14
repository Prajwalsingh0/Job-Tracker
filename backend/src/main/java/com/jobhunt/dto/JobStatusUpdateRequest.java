package com.jobhunt.dto;

import com.jobhunt.entity.JobStatus;
import jakarta.validation.constraints.NotNull;

public record JobStatusUpdateRequest(
        @NotNull(message = "Status is required")
        JobStatus status
) {
}

package com.jobhunt.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GenerateRequest(
        @NotNull(message = "A job id is required")
        Long jobId,

        @NotNull(message = "A task is required")
        AiTask task,

        @Size(max = 20000, message = "Resume text must be at most 20000 characters")
        String resumeText
) {
}

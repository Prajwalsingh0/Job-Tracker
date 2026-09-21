package com.jobhunt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Matching runs against the candidate's own text. There is no stored resume parsing, so the
 * caller supplies what it wants compared.
 */
public record MatchRequest(
        @NotNull(message = "A job id is required")
        Long jobId,

        @NotBlank(message = "Paste some resume text to compare")
        @Size(max = 20000, message = "Resume text must be at most 20000 characters")
        String resumeText
) {
}

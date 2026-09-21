package com.jobhunt.dto;

import java.util.List;

/**
 * Result of the deterministic matcher.
 *
 * <p>Deliberately reported as keyword overlap with an explicit {@code disclaimer}, not as an
 * ATS score: the number is literal word overlap between two pieces of text and nothing more.
 */
public record MatchResponse(
        Long jobId,
        String companyName,
        String jobTitle,
        /** Percentage of the job's recognised skills that appear in the supplied text. */
        int skillCoverage,
        /** Percentage of the job's significant words that appear in the supplied text. */
        int keywordOverlap,
        String method,
        String disclaimer,
        List<String> jobSkills,
        List<String> matchedSkills,
        List<String> missingSkills,
        List<String> suggestions
) {
}

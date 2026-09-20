package com.jobhunt.dto;

import com.jobhunt.entity.ResumeFileType;

import java.time.Instant;

/**
 * Cover letter metadata. {@code body} is present for pasted text, {@code fileName} for an
 * uploaded document; a letter may have either or both.
 */
public record CoverLetterDto(
        Long id,
        String name,
        Long jobId,
        String body,
        String fileName,
        ResumeFileType fileType,
        Long fileSize,
        String versionTag,
        Instant createdAt
) {
}

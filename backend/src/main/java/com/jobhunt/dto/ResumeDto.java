package com.jobhunt.dto;

import com.jobhunt.entity.ResumeFileType;

import java.time.Instant;

/** Resume metadata. Document bytes are only served by the download endpoint. */
public record ResumeDto(
        Long id,
        String name,
        String fileName,
        ResumeFileType fileType,
        String versionTag,
        long usageCount,
        Instant createdAt
) {
}

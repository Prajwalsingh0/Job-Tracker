package com.jobhunt.dto;

/** Dashboard metrics. Rate fields are percentages (0-100). */
public record JobStatsDto(
        long total,
        long wishlist,
        long applied,
        long interviewing,
        long offers,
        long rejected,
        double responseRate,
        double interviewRate
) {
}

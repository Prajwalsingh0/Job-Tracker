package com.jobhunt.dto;

import com.jobhunt.entity.JobStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * Dashboard analytics for a date range. Everything is scoped to the requesting user.
 *
 * <p>Rates are percentages (0-100). {@code applied} means an application was actually
 * submitted, i.e. any status past the wishlist.
 */
public record AnalyticsDto(
        LocalDate from,
        LocalDate to,
        Metrics metrics,
        List<FunnelStage> funnel,
        List<TimelinePoint> timeline,
        List<Breakdown> companies,
        List<Breakdown> roles,
        List<UpcomingDeadline> upcomingDeadlines,
        List<ActiveInterview> activeInterviews
) {

    public record Metrics(
            long total,
            long applied,
            long interviewing,
            long offers,
            long rejected,
            double responseRate,
            double interviewRate,
            double offerRate
    ) {
    }

    /** One bar of the application funnel, in pipeline order. */
    public record FunnelStage(JobStatus status, long count) {
    }

    /** Counts of entries into each stage during a calendar month ({@code period} = YYYY-MM). */
    public record TimelinePoint(String period, long applied, long interviews, long offers) {
    }

    /** Totals grouped by company or by role. */
    public record Breakdown(String label, long total, long interviewing, long offers) {
    }

    public record UpcomingDeadline(Long jobId, String companyName, String jobTitle, LocalDate deadline) {
    }

    public record ActiveInterview(Long jobId, String companyName, String jobTitle, JobStatus status) {
    }
}

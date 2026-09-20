package com.jobhunt.service;

import com.jobhunt.dto.AnalyticsDto;
import com.jobhunt.entity.Job;
import com.jobhunt.entity.JobStatus;
import com.jobhunt.entity.JobStatusHistory;
import com.jobhunt.repository.JobRepository;
import com.jobhunt.repository.JobStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Aggregates the user's job data into the analytics payload.
 *
 * <p>The whole set is loaded and reduced in memory. That is deliberate for a personal
 * tracker (hundreds of rows) and keeps the aggregation readable; at a larger scale these
 * would become SQL aggregate queries or a warehouse.
 */
@Service
public class AnalyticsService {

    /** Pipeline order used for the funnel, excluding the terminal "withdrawn"/"ghosted". */
    private static final List<JobStatus> FUNNEL_ORDER = List.of(
            JobStatus.WISHLIST,
            JobStatus.APPLIED,
            JobStatus.PHONE_SCREEN,
            JobStatus.INTERVIEW,
            JobStatus.OFFER,
            JobStatus.REJECTED);

    /** Outcomes that count as "the employer responded". */
    private static final Set<JobStatus> RESPONDED = EnumSet.of(
            JobStatus.PHONE_SCREEN, JobStatus.INTERVIEW, JobStatus.OFFER,
            JobStatus.REJECTED, JobStatus.WITHDRAWN);

    private static final Set<JobStatus> INTERVIEWING =
            EnumSet.of(JobStatus.PHONE_SCREEN, JobStatus.INTERVIEW);

    private static final Set<JobStatus> ACTIVE_INTERVIEW_STATUSES =
            EnumSet.of(JobStatus.PHONE_SCREEN, JobStatus.INTERVIEW);

    /** How far ahead "upcoming" deadlines look. */
    private static final int DEADLINE_HORIZON_DAYS = 60;

    /** Default window when the caller does not specify one. */
    private static final int DEFAULT_MONTHS = 6;

    private final JobRepository jobRepository;
    private final JobStatusHistoryRepository historyRepository;

    public AnalyticsService(JobRepository jobRepository, JobStatusHistoryRepository historyRepository) {
        this.jobRepository = jobRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsDto analyse(Long userId, LocalDate requestedFrom, LocalDate requestedTo) {
        LocalDate to = requestedTo != null ? requestedTo : LocalDate.now();
        LocalDate from = requestedFrom != null ? requestedFrom : to.minusMonths(DEFAULT_MONTHS).withDayOfMonth(1);
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must be on or before 'to'");
        }

        List<Job> jobs = jobRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .filter(job -> withinRange(job, from, to))
                .toList();

        return new AnalyticsDto(
                from,
                to,
                buildMetrics(jobs),
                buildFunnel(jobs),
                buildTimeline(userId, from, to),
                buildBreakdown(jobs, Job::getCompanyName),
                buildBreakdown(jobs, Job::getJobTitle),
                buildUpcomingDeadlines(jobs),
                buildActiveInterviews(jobs));
    }

    /** A job counts towards the range by the date it was applied for, else when it was added. */
    private boolean withinRange(Job job, LocalDate from, LocalDate to) {
        LocalDate effective = job.getAppliedDate() != null
                ? job.getAppliedDate()
                : job.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
        return !effective.isBefore(from) && !effective.isAfter(to);
    }

    private AnalyticsDto.Metrics buildMetrics(List<Job> jobs) {
        long total = jobs.size();
        long wishlist = count(jobs, job -> job.getStatus() == JobStatus.WISHLIST);
        long applied = total - wishlist;
        long interviewing = count(jobs, job -> INTERVIEWING.contains(job.getStatus()));
        long offers = count(jobs, job -> job.getStatus() == JobStatus.OFFER);
        long rejected = count(jobs, job -> job.getStatus() == JobStatus.REJECTED);
        long responded = count(jobs, job -> RESPONDED.contains(job.getStatus()));

        return new AnalyticsDto.Metrics(
                total,
                applied,
                interviewing,
                offers,
                rejected,
                percentage(responded, applied),
                percentage(interviewing, applied),
                percentage(offers, applied));
    }

    private List<AnalyticsDto.FunnelStage> buildFunnel(List<Job> jobs) {
        return FUNNEL_ORDER.stream()
                .map(status -> new AnalyticsDto.FunnelStage(
                        status,
                        count(jobs, job -> job.getStatus() == status)))
                .toList();
    }

    /**
     * Monthly counts of entries into "applied", "interviewing" and "offer" stages, taken
     * from the status history rather than inferred from the current state. Every month in
     * the range is present, even with zeroes, so the chart axis stays continuous.
     */
    private List<AnalyticsDto.TimelinePoint> buildTimeline(Long userId, LocalDate from, LocalDate to) {
        Instant rangeStart = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant rangeEnd = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        Map<YearMonth, long[]> byMonth = new LinkedHashMap<>();
        for (YearMonth month = YearMonth.from(from); !month.isAfter(YearMonth.from(to)); month = month.plusMonths(1)) {
            byMonth.put(month, new long[3]);
        }

        for (JobStatusHistory entry : historyRepository.findByJob_UserIdOrderByChangedAtAsc(userId)) {
            Instant when = entry.getChangedAt();
            if (when == null || when.isBefore(rangeStart) || !when.isBefore(rangeEnd)) {
                continue;
            }
            long[] counters = byMonth.get(YearMonth.from(when.atZone(ZoneOffset.UTC)));
            if (counters == null) {
                continue;
            }
            JobStatus target = entry.getToStatus();
            if (target == JobStatus.APPLIED) {
                counters[0]++;
            } else if (INTERVIEWING.contains(target)) {
                counters[1]++;
            } else if (target == JobStatus.OFFER) {
                counters[2]++;
            }
        }

        List<AnalyticsDto.TimelinePoint> points = new ArrayList<>();
        byMonth.forEach((month, counters) -> points.add(
                new AnalyticsDto.TimelinePoint(month.toString(), counters[0], counters[1], counters[2])));
        return points;
    }

    private List<AnalyticsDto.Breakdown> buildBreakdown(List<Job> jobs, Function<Job, String> key) {
        Map<String, List<Job>> grouped = jobs.stream()
                .filter(job -> key.apply(job) != null && !key.apply(job).isBlank())
                .collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()));

        return grouped.entrySet().stream()
                .map(entry -> new AnalyticsDto.Breakdown(
                        entry.getKey(),
                        entry.getValue().size(),
                        count(entry.getValue(), job -> INTERVIEWING.contains(job.getStatus())),
                        count(entry.getValue(), job -> job.getStatus() == JobStatus.OFFER)))
                .sorted(Comparator.comparingLong(AnalyticsDto.Breakdown::total).reversed()
                        .thenComparing(AnalyticsDto.Breakdown::label))
                .limit(10)
                .toList();
    }

    private List<AnalyticsDto.UpcomingDeadline> buildUpcomingDeadlines(List<Job> jobs) {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(DEADLINE_HORIZON_DAYS);

        return jobs.stream()
                .filter(job -> job.getDeadline() != null)
                .filter(job -> !job.getDeadline().isBefore(today) && !job.getDeadline().isAfter(horizon))
                .sorted(Comparator.comparing(Job::getDeadline))
                .limit(10)
                .map(job -> new AnalyticsDto.UpcomingDeadline(
                        job.getId(), job.getCompanyName(), job.getJobTitle(), job.getDeadline()))
                .toList();
    }

    private List<AnalyticsDto.ActiveInterview> buildActiveInterviews(List<Job> jobs) {
        return jobs.stream()
                .filter(job -> ACTIVE_INTERVIEW_STATUSES.contains(job.getStatus()))
                .sorted(Comparator.comparing(Job::getUpdatedAt).reversed())
                .limit(10)
                .map(job -> new AnalyticsDto.ActiveInterview(
                        job.getId(), job.getCompanyName(), job.getJobTitle(), job.getStatus()))
                .toList();
    }

    private long count(List<Job> jobs, java.util.function.Predicate<Job> predicate) {
        return jobs.stream().filter(predicate).count();
    }

    private double percentage(long numerator, long denominator) {
        return denominator > 0 ? (numerator * 100.0) / denominator : 0.0;
    }
}

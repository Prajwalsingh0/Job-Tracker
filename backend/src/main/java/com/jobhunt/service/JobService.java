package com.jobhunt.service;

import com.jobhunt.dto.JobDto;
import com.jobhunt.dto.JobRequest;
import com.jobhunt.dto.JobStatsDto;
import com.jobhunt.entity.Job;
import com.jobhunt.entity.JobOutcome;
import com.jobhunt.entity.JobStatus;
import com.jobhunt.entity.Resume;
import com.jobhunt.entity.User;
import com.jobhunt.exception.ResourceNotFoundException;
import com.jobhunt.repository.JobRepository;
import com.jobhunt.repository.ResumeRepository;
import com.jobhunt.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * All job operations are scoped to the owning user, so one account can never read or
 * mutate another account's records.
 */
@Service
public class JobService {

    /** Statuses that count as "no response yet" when computing the response rate. */
    private static final Set<JobStatus> AWAITING_RESPONSE =
            EnumSet.of(JobStatus.WISHLIST, JobStatus.APPLIED, JobStatus.GHOSTED);

    private final JobRepository jobRepository;
    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;

    public JobService(JobRepository jobRepository,
                      ResumeRepository resumeRepository,
                      UserRepository userRepository) {
        this.jobRepository = jobRepository;
        this.resumeRepository = resumeRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<JobDto> list(Long userId, String search, JobStatus status) {
        String term = search == null ? null : search.trim().toLowerCase(Locale.ROOT);
        return jobRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .filter(job -> status == null || job.getStatus() == status)
                .filter(job -> matchesSearch(job, term))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public JobDto get(Long userId, Long jobId) {
        return toDto(findOwned(userId, jobId));
    }

    @Transactional
    public JobDto create(Long userId, JobRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Job job = new Job();
        job.setUser(user);
        applyRequest(job, request, userId);

        return toDto(jobRepository.save(job));
    }

    @Transactional
    public JobDto update(Long userId, Long jobId, JobRequest request) {
        Job job = findOwned(userId, jobId);
        applyRequest(job, request, userId);
        return toDto(jobRepository.save(job));
    }

    @Transactional
    public JobDto updateStatus(Long userId, Long jobId, JobStatus status) {
        Job job = findOwned(userId, jobId);
        job.setStatus(status);
        job.setAppliedDate(resolveAppliedDate(status, null, job.getAppliedDate()));
        job.setOutcome(outcomeFor(status));
        return toDto(jobRepository.save(job));
    }

    @Transactional
    public void delete(Long userId, Long jobId) {
        jobRepository.delete(findOwned(userId, jobId));
    }

    @Transactional(readOnly = true)
    public JobStatsDto stats(Long userId) {
        List<Job> jobs = jobRepository.findByUserIdOrderByUpdatedAtDesc(userId);

        long total = jobs.size();
        long wishlist = jobs.stream().filter(job -> job.getStatus() == JobStatus.WISHLIST).count();
        long applied = total - wishlist;
        long interviewing = jobs.stream()
                .filter(job -> job.getStatus() == JobStatus.PHONE_SCREEN || job.getStatus() == JobStatus.INTERVIEW)
                .count();
        long offers = jobs.stream().filter(job -> job.getStatus() == JobStatus.OFFER).count();
        long rejected = jobs.stream().filter(job -> job.getStatus() == JobStatus.REJECTED).count();
        long responded = jobs.stream().filter(job -> !AWAITING_RESPONSE.contains(job.getStatus())).count();

        double responseRate = applied > 0 ? (responded * 100.0) / applied : 0.0;
        double interviewRate = applied > 0 ? (interviewing * 100.0) / applied : 0.0;

        return new JobStatsDto(total, wishlist, applied, interviewing, offers, rejected, responseRate, interviewRate);
    }

    private Job findOwned(Long userId, Long jobId) {
        return jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> ResourceNotFoundException.job(jobId));
    }

    private void applyRequest(Job job, JobRequest request, Long userId) {
        JobStatus status = request.status() != null ? request.status() : JobStatus.WISHLIST;

        job.setCompanyName(request.companyName().trim());
        job.setJobTitle(request.jobTitle().trim());
        job.setJobUrl(trimToNull(request.jobUrl()));
        job.setDescription(trimToNull(request.description()));
        job.setLocation(trimToNull(request.location()));
        job.setSalaryRange(trimToNull(request.salaryRange()));
        job.setNotes(trimToNull(request.notes()));
        job.setFeedback(trimToNull(request.feedback()));
        job.setTargetApplyDate(request.targetApplyDate());
        job.setOutcomeReason(request.outcomeReason());
        job.setStatus(status);
        job.setAppliedDate(resolveAppliedDate(status, request.appliedDate(), job.getAppliedDate()));
        job.setOutcome(outcomeFor(status));
        job.setResume(resolveResume(userId, request.resumeId()));
    }

    /**
     * Wishlist entries have no application date. Everything else keeps the supplied date,
     * falls back to the previously stored one, and finally defaults to today.
     */
    private LocalDate resolveAppliedDate(JobStatus status, LocalDate requested, LocalDate existing) {
        if (status == JobStatus.WISHLIST) {
            return null;
        }
        if (requested != null) {
            return requested;
        }
        if (existing != null) {
            return existing;
        }
        return LocalDate.now();
    }

    /** The outcome is always derived from the status so the two can never disagree. */
    private JobOutcome outcomeFor(JobStatus status) {
        return switch (status) {
            case OFFER -> JobOutcome.OFFER;
            case REJECTED -> JobOutcome.REJECTED;
            case WITHDRAWN -> JobOutcome.WITHDRAWN;
            case GHOSTED -> JobOutcome.GHOSTED;
            default -> null;
        };
    }

    private Resume resolveResume(Long userId, Long resumeId) {
        if (resumeId == null) {
            return null;
        }
        return resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> ResourceNotFoundException.resume(resumeId));
    }

    private boolean matchesSearch(Job job, String term) {
        if (term == null || term.isEmpty()) {
            return true;
        }
        return contains(job.getCompanyName(), term)
                || contains(job.getJobTitle(), term)
                || contains(job.getLocation(), term);
    }

    private boolean contains(String value, String term) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(term);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private JobDto toDto(Job job) {
        return new JobDto(
                job.getId(),
                job.getCompanyName(),
                job.getJobTitle(),
                job.getJobUrl(),
                job.getDescription(),
                job.getLocation(),
                job.getSalaryRange(),
                job.getStatus(),
                job.getAppliedDate(),
                job.getTargetApplyDate(),
                job.getOutcome(),
                job.getOutcomeReason(),
                job.getFeedback(),
                job.getNotes(),
                job.getResume() != null ? job.getResume().getId() : null,
                job.getCreatedAt(),
                job.getUpdatedAt());
    }
}

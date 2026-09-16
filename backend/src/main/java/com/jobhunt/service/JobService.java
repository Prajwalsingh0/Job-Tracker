package com.jobhunt.service;

import com.jobhunt.dto.JobDto;
import com.jobhunt.dto.JobRequest;
import com.jobhunt.dto.JobStatsDto;
import com.jobhunt.dto.JobStatusHistoryDto;
import com.jobhunt.dto.PageResponse;
import com.jobhunt.entity.Job;
import com.jobhunt.entity.JobOutcome;
import com.jobhunt.entity.JobStatus;
import com.jobhunt.entity.JobStatusHistory;
import com.jobhunt.entity.Resume;
import com.jobhunt.entity.User;
import com.jobhunt.exception.ConflictException;
import com.jobhunt.exception.ResourceNotFoundException;
import com.jobhunt.repository.JobRepository;
import com.jobhunt.repository.JobStatusHistoryRepository;
import com.jobhunt.repository.ResumeRepository;
import com.jobhunt.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * All job operations are scoped to the owning user, so one account can never read or
 * mutate another account's records.
 *
 * <p>Filtering, sorting and pagination are pushed down to the database through JPA
 * specifications rather than being applied to an in-memory list.
 */
@Service
public class JobService {

    /** Statuses that count as "no response yet" when computing the response rate. */
    private static final Set<JobStatus> AWAITING_RESPONSE =
            EnumSet.of(JobStatus.WISHLIST, JobStatus.APPLIED, JobStatus.GHOSTED);

    /** Default page size when the client does not ask for one. */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** Upper bound so a client cannot request an unbounded page. */
    private static final int MAX_PAGE_SIZE = 500;

    /**
     * Client-facing sort field -> entity property. A whitelist, so a client can never
     * inject an arbitrary property path into the query.
     */
    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "updatedAt", "updatedAt",
            "createdAt", "createdAt",
            "companyName", "companyName",
            "jobTitle", "jobTitle",
            "status", "status",
            "appliedDate", "appliedDate",
            "targetApplyDate", "targetApplyDate");

    private static final String DEFAULT_SORT_FIELD = "updatedAt";

    private static final List<String> CSV_HEADERS = List.of(
            "id", "companyName", "jobTitle", "status", "location", "salaryRange",
            "jobUrl", "appliedDate", "targetApplyDate", "outcome", "outcomeReason",
            "resumeId", "createdAt", "updatedAt");

    private final JobRepository jobRepository;
    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final JobStatusHistoryRepository historyRepository;

    public JobService(JobRepository jobRepository,
                      ResumeRepository resumeRepository,
                      UserRepository userRepository,
                      JobStatusHistoryRepository historyRepository) {
        this.jobRepository = jobRepository;
        this.resumeRepository = resumeRepository;
        this.userRepository = userRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<JobDto> list(Long userId,
                                     String search,
                                     JobStatus status,
                                     int page,
                                     int size,
                                     String sortBy,
                                     String direction) {

        Specification<Job> specification = Specification
                .where(ownedBy(userId))
                .and(hasStatus(status))
                .and(matchesSearch(search));

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                normaliseSize(size),
                resolveSort(sortBy, direction));

        return PageResponse.of(jobRepository.findAll(specification, pageable), this::toDto);
    }

    @Transactional(readOnly = true)
    public JobDto get(Long userId, Long jobId) {
        return toDto(findOwned(userId, jobId));
    }

    @Transactional
    public JobDto create(Long userId, JobRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        rejectDuplicate(userId, request.companyName(), request.jobTitle(), null);

        Job job = new Job();
        job.setUser(user);
        applyRequest(job, request, userId);

        Job saved = jobRepository.save(job);
        recordHistory(saved, null, saved.getStatus());
        return toDto(saved);
    }

    @Transactional
    public JobDto update(Long userId, Long jobId, JobRequest request) {
        Job job = findOwned(userId, jobId);
        rejectDuplicate(userId, request.companyName(), request.jobTitle(), jobId);

        JobStatus previousStatus = job.getStatus();
        applyRequest(job, request, userId);

        Job saved = jobRepository.save(job);
        recordHistory(saved, previousStatus, saved.getStatus());
        return toDto(saved);
    }

    @Transactional
    public JobDto updateStatus(Long userId, Long jobId, JobStatus status) {
        Job job = findOwned(userId, jobId);
        JobStatus previousStatus = job.getStatus();

        job.setStatus(status);
        job.setAppliedDate(resolveAppliedDate(status, null, job.getAppliedDate()));
        job.setOutcome(outcomeFor(status));

        Job saved = jobRepository.save(job);
        recordHistory(saved, previousStatus, saved.getStatus());
        return toDto(saved);
    }

    /** Pipeline transitions for one job, oldest first. Ownership is enforced. */
    @Transactional(readOnly = true)
    public List<JobStatusHistoryDto> history(Long userId, Long jobId) {
        findOwned(userId, jobId);
        return historyRepository.findByJobIdOrderByChangedAtAsc(jobId).stream()
                .map(this::toHistoryDto)
                .toList();
    }

    /** Most recent transitions across the user's jobs, newest first. */
    @Transactional(readOnly = true)
    public List<JobStatusHistoryDto> recentActivity(Long userId) {
        return historyRepository.findTop20ByJob_UserIdOrderByChangedAtDesc(userId).stream()
                .map(this::toHistoryDto)
                .toList();
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
        // "Applied" means an application was actually submitted, i.e. anything past the wishlist.
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

    /**
     * Exports the caller's jobs (honouring the same search/status filters as the list
     * endpoint) as RFC 4180 CSV. A UTF-8 BOM is included so Excel detects the encoding.
     */
    @Transactional(readOnly = true)
    public byte[] exportCsv(Long userId, String search, JobStatus status) {
        Specification<Job> specification = Specification
                .where(ownedBy(userId))
                .and(hasStatus(status))
                .and(matchesSearch(search));

        List<Job> jobs = jobRepository.findAll(specification, resolveSort(DEFAULT_SORT_FIELD, "desc"));

        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", CSV_HEADERS)).append("\r\n");

        for (Job job : jobs) {
            csv.append(csvRow(job)).append("\r\n");
        }

        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        return withBom;
    }

    // ---------------------------------------------------------------- filtering

    private Specification<Job> ownedBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    private Specification<Job> hasStatus(JobStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private Specification<Job> matchesSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("companyName")), pattern),
                cb.like(cb.lower(root.get("jobTitle")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("location"), "")), pattern));
    }

    private int normaliseSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private Sort resolveSort(String sortBy, String direction) {
        String property = SORTABLE_FIELDS.getOrDefault(
                sortBy == null ? "" : sortBy.trim(),
                DEFAULT_SORT_FIELD);
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        // updatedAt is the natural tie-breaker so paging is stable.
        if (DEFAULT_SORT_FIELD.equals(property)) {
            return Sort.by(sortDirection, property);
        }
        return Sort.by(sortDirection, property).and(Sort.by(Sort.Direction.DESC, DEFAULT_SORT_FIELD));
    }

    // ---------------------------------------------------------------- mapping

    private Job findOwned(Long userId, Long jobId) {
        return jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> ResourceNotFoundException.job(jobId));
    }

    private void rejectDuplicate(Long userId, String companyName, String jobTitle, Long excludingJobId) {
        String company = companyName.trim();
        String title = jobTitle.trim();
        boolean exists = excludingJobId == null
                ? jobRepository.existsByUserIdAndCompanyNameIgnoreCaseAndJobTitleIgnoreCase(userId, company, title)
                : jobRepository.existsByUserIdAndCompanyNameIgnoreCaseAndJobTitleIgnoreCaseAndIdNot(
                        userId, company, title, excludingJobId);

        if (exists) {
            throw new ConflictException(
                    "You are already tracking \"" + title + "\" at " + company + ".");
        }
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
        job.setJobSource(trimToNull(request.jobSource()));
        job.setWorkMode(request.workMode());
        job.setDeadline(request.deadline());
        job.setSalaryMin(request.salaryMin());
        job.setSalaryMax(request.salaryMax());
        job.setSalaryCurrency(normaliseCurrency(request.salaryCurrency()));
        job.setTags(normaliseTags(request.tags()));

        if (request.salaryMin() != null && request.salaryMax() != null
                && request.salaryMin() > request.salaryMax()) {
            throw new IllegalArgumentException("Minimum salary cannot be greater than maximum salary");
        }
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
                job.getJobSource(),
                job.getWorkMode(),
                job.getDeadline(),
                job.getSalaryMin(),
                job.getSalaryMax(),
                job.getSalaryCurrency(),
                job.getTags() == null ? List.of() : job.getTags().stream().sorted().toList(),
                job.getResume() != null ? job.getResume().getId() : null,
                job.getCreatedAt(),
                job.getUpdatedAt());
    }

    private String csvRow(Job job) {
        return String.join(",",
                csv(job.getId()),
                csv(job.getCompanyName()),
                csv(job.getJobTitle()),
                csv(job.getStatus() == null ? null : job.getStatus().getValue()),
                csv(job.getLocation()),
                csv(job.getSalaryRange()),
                csv(job.getJobUrl()),
                csv(job.getAppliedDate()),
                csv(job.getTargetApplyDate()),
                csv(job.getOutcome() == null ? null : job.getOutcome().getValue()),
                csv(job.getOutcomeReason() == null ? null : job.getOutcomeReason().getValue()),
                csv(job.getResume() != null ? job.getResume().getId() : null),
                csv(job.getCreatedAt()),
                csv(job.getUpdatedAt()));
    }

    /** RFC 4180 field escaping: wrap in quotes whenever the value contains , " CR or LF. */
    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    private void recordHistory(Job job, JobStatus from, JobStatus to) {
        if (to == null || Objects.equals(from, to)) {
            return;
        }
        historyRepository.save(new JobStatusHistory(job, from, to, null));
    }

    private JobStatusHistoryDto toHistoryDto(JobStatusHistory entry) {
        Job job = entry.getJob();
        return new JobStatusHistoryDto(
                entry.getId(),
                job == null ? null : job.getId(),
                job == null ? null : job.getCompanyName(),
                job == null ? null : job.getJobTitle(),
                entry.getFromStatus(),
                entry.getToStatus(),
                entry.getChangedAt(),
                entry.getNote());
    }

    private String normaliseCurrency(String currency) {
        String trimmed = trimToNull(currency);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    /** Trims, de-duplicates and caps the tag list so the collection table stays tidy. */
    private Set<String> normaliseTags(Set<String> tags) {
        Set<String> cleaned = new LinkedHashSet<>();
        if (tags == null) {
            return cleaned;
        }
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) {
                cleaned.add(trimmed);
            }
            if (cleaned.size() >= 20) {
                break;
            }
        }
        return cleaned;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

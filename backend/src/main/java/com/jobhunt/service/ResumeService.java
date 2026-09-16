package com.jobhunt.service;

import com.jobhunt.dto.ResumeDto;
import com.jobhunt.entity.Resume;
import com.jobhunt.entity.ResumeFileType;
import com.jobhunt.entity.User;
import com.jobhunt.exception.ResourceNotFoundException;
import com.jobhunt.repository.JobRepository;
import com.jobhunt.repository.ResumeRepository;
import com.jobhunt.repository.UserRepository;
import com.jobhunt.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resume metadata lives in the database; document bytes live in {@link FileStorageService}.
 * The database never holds file content, so listing resumes is a metadata-only query.
 */
@Service
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    private static final String PDF = "application/pdf";
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(PDF, DOCX);

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    private final ResumeRepository resumeRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final FileStorageService storage;

    public ResumeService(ResumeRepository resumeRepository,
                         JobRepository jobRepository,
                         UserRepository userRepository,
                         FileStorageService storage) {
        this.resumeRepository = resumeRepository;
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.storage = storage;
    }

    @Transactional
    public ResumeDto upload(Long userId, MultipartFile file, String name, String versionTag) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A resume file is required");
        }

        String contentType = file.getContentType();
        String normalizedType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(normalizedType)) {
            throw new IllegalArgumentException("Only PDF and DOCX files are supported");
        }
        if (file.getSize() > Resume.MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File size must be less than 10MB");
        }

        ResumeFileType fileType = normalizedType.equals(PDF) ? ResumeFileType.PDF : ResumeFileType.DOCX;

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read the uploaded file", ex);
        }

        // The browser-supplied content type is not trustworthy: confirm the bytes really are
        // the format the file claims to be before storing anything.
        validateMagicBytes(bytes, fileType);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        String originalFileName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename()
                : "resume";

        Resume resume = new Resume();
        resume.setUser(user);
        resume.setFileName(originalFileName);
        resume.setContentType(normalizedType);
        resume.setFileType(fileType);
        resume.setFileSize(bytes.length);
        resume.setStorageKey(storage.store(bytes, originalFileName));
        resume.setName(StringUtils.hasText(name) ? name.trim() : stripExtension(originalFileName));
        resume.setVersionTag(StringUtils.hasText(versionTag) ? versionTag.trim() : null);

        return toDto(resumeRepository.save(resume), 0L);
    }

    @Transactional(readOnly = true)
    public List<ResumeDto> list(Long userId) {
        Map<Long, Long> usageCounts = usageCounts(userId);
        return resumeRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(resume -> toDto(resume, usageCounts.getOrDefault(resume.getId(), 0L)))
                .toList();
    }

    /** Resolves the metadata and a streamable handle to the stored document. */
    @Transactional(readOnly = true)
    public ResumeDocument download(Long userId, Long resumeId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> ResourceNotFoundException.resume(resumeId));

        if (!StringUtils.hasText(resume.getStorageKey())) {
            throw new ResourceNotFoundException("The document for this resume is not available");
        }

        return new ResumeDocument(resume, storage.loadAsResource(resume.getStorageKey()));
    }

    @Transactional
    public void delete(Long userId, Long resumeId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> ResourceNotFoundException.resume(resumeId));

        // Jobs keep existing but lose the reference to the deleted document.
        jobRepository.clearResumeReference(resume.getId());
        resumeRepository.delete(resume);
        storage.delete(resume.getStorageKey());
    }

    /**
     * One-time housekeeping: moves documents that were stored in the {@code file_data}
     * column into file storage and clears the column. Safe to run on every startup because
     * it only selects rows that have bytes and no storage key yet.
     */
    @Transactional
    public int migrateLegacyBlobs() {
        List<Resume> legacy = resumeRepository.findByStorageKeyIsNullAndFileDataIsNotNull();

        int migrated = 0;
        for (Resume resume : legacy) {
            byte[] bytes = resume.getFileData();
            if (bytes == null || bytes.length == 0) {
                continue;
            }

            resume.setStorageKey(storage.store(bytes, resume.getFileName()));
            resume.setFileSize(bytes.length);
            // Release the bytes from the database.
            resume.setFileData(null);
            resumeRepository.save(resume);
            migrated++;
        }

        if (migrated > 0) {
            log.info("Moved {} legacy resume document(s) from the database into file storage", migrated);
        }
        return migrated;
    }

    /**
     * PDF files start with {@code %PDF-}; DOCX is a ZIP container starting with
     * {@code PK\x03\x04}. Checking the signature stops a renamed executable being stored
     * as a "resume".
     */
    private void validateMagicBytes(byte[] bytes, ResumeFileType fileType) {
        byte[] expected = fileType == ResumeFileType.PDF ? PDF_MAGIC : ZIP_MAGIC;

        if (bytes.length < expected.length) {
            throw new IllegalArgumentException("The uploaded file is empty or truncated");
        }

        for (int i = 0; i < expected.length; i++) {
            if (bytes[i] != expected[i]) {
                throw new IllegalArgumentException(fileType == ResumeFileType.PDF
                        ? "The file does not appear to be a valid PDF"
                        : "The file does not appear to be a valid DOCX document");
            }
        }
    }

    private Map<Long, Long> usageCounts(Long userId) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : jobRepository.countJobsByResumeForUser(userId)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private ResumeDto toDto(Resume resume, long usageCount) {
        return new ResumeDto(
                resume.getId(),
                resume.getName(),
                resume.getFileName(),
                resume.getFileType(),
                resume.getVersionTag(),
                usageCount,
                resume.getCreatedAt());
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /** Metadata plus a streamable handle to the stored document. */
    public record ResumeDocument(Resume resume, Resource resource) {
    }
}

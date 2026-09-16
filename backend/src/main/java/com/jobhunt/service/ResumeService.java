package com.jobhunt.service;

import com.jobhunt.dto.ResumeDto;
import com.jobhunt.entity.Resume;
import com.jobhunt.entity.ResumeFileType;
import com.jobhunt.entity.User;
import com.jobhunt.exception.ResourceNotFoundException;
import com.jobhunt.repository.JobRepository;
import com.jobhunt.repository.ResumeRepository;
import com.jobhunt.repository.UserRepository;
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

@Service
public class ResumeService {

    private static final String PDF = "application/pdf";
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(PDF, DOCX);

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    private final ResumeRepository resumeRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;

    public ResumeService(ResumeRepository resumeRepository,
                         JobRepository jobRepository,
                         UserRepository userRepository) {
        this.resumeRepository = resumeRepository;
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
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

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        String originalFileName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename()
                : "resume";

        Resume resume = new Resume();
        resume.setUser(user);
        resume.setFileName(originalFileName);
        resume.setContentType(normalizedType);
        resume.setFileType(normalizedType.equals(PDF) ? ResumeFileType.PDF : ResumeFileType.DOCX);
        resume.setFileSize(file.getSize());
        resume.setName(StringUtils.hasText(name) ? name.trim() : stripExtension(originalFileName));
        resume.setVersionTag(StringUtils.hasText(versionTag) ? versionTag.trim() : null);

        try {
            byte[] bytes = file.getBytes();

            // The browser-supplied Content-Type is not trustworthy: confirm the bytes really
            // are the format the file claims to be before storing anything.
            validateMagicBytes(bytes, resume.getFileType());

            resume.setFileData(bytes);
            resume.setFileSize(bytes.length);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read the uploaded file", ex);
        }

        return toDto(resumeRepository.save(resume), 0L);
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

    @Transactional(readOnly = true)
    public List<ResumeDto> list(Long userId) {
        Map<Long, Long> usageCounts = usageCounts(userId);
        return resumeRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(resume -> toDto(resume, usageCounts.getOrDefault(resume.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Resume download(Long userId, Long resumeId) {
        return resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> ResourceNotFoundException.resume(resumeId));
    }

    @Transactional
    public void delete(Long userId, Long resumeId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> ResourceNotFoundException.resume(resumeId));
        // Jobs keep existing but lose the reference to the deleted document.
        jobRepository.clearResumeReference(resume.getId());
        resumeRepository.delete(resume);
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
}

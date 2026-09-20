package com.jobhunt.service;

import com.jobhunt.dto.CoverLetterDto;
import com.jobhunt.entity.CoverLetter;
import com.jobhunt.entity.Job;
import com.jobhunt.entity.ResumeFileType;
import com.jobhunt.entity.User;
import com.jobhunt.exception.ResourceNotFoundException;
import com.jobhunt.repository.CoverLetterRepository;
import com.jobhunt.repository.JobRepository;
import com.jobhunt.repository.UserRepository;
import com.jobhunt.storage.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Cover letters. A letter is either an uploaded document, pasted text, or both; the
 * document bytes go to {@link FileStorageService} exactly like resumes.
 */
@Service
public class CoverLetterService {

    private static final String PDF = "application/pdf";
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(PDF, DOCX);

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    /** Same ceiling as a resume upload. */
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    private final CoverLetterRepository coverLetterRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final FileStorageService storage;

    public CoverLetterService(CoverLetterRepository coverLetterRepository,
                              JobRepository jobRepository,
                              UserRepository userRepository,
                              FileStorageService storage) {
        this.coverLetterRepository = coverLetterRepository;
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.storage = storage;
    }

    @Transactional
    public CoverLetterDto create(Long userId,
                                 MultipartFile file,
                                 String body,
                                 String name,
                                 String versionTag,
                                 Long jobId) {

        boolean hasFile = file != null && !file.isEmpty();
        String trimmedBody = trimToNull(body);

        if (!hasFile && trimmedBody == null) {
            throw new IllegalArgumentException("Provide a cover letter document or some text");
        }
        if (trimmedBody != null && trimmedBody.length() > CoverLetter.MAX_BODY_LENGTH) {
            throw new IllegalArgumentException(
                    "Cover letter text must be at most " + CoverLetter.MAX_BODY_LENGTH + " characters");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        CoverLetter letter = new CoverLetter();
        letter.setUser(user);
        letter.setJob(resolveJob(userId, jobId));
        letter.setBody(trimmedBody);
        letter.setVersionTag(trimToNull(versionTag));

        if (hasFile) {
            attachDocument(letter, file);
        }

        letter.setName(resolveName(name, letter));
        return toDto(coverLetterRepository.save(letter));
    }

    @Transactional(readOnly = true)
    public List<CoverLetterDto> list(Long userId) {
        return coverLetterRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDto)
                .toList();
    }

    /** Resolves the metadata and a streamable handle to the stored document. */
    @Transactional(readOnly = true)
    public CoverLetterDocument download(Long userId, Long coverLetterId) {
        CoverLetter letter = coverLetterRepository.findByIdAndUserId(coverLetterId, userId)
                .orElseThrow(() -> ResourceNotFoundException.coverLetter(coverLetterId));

        if (!letter.hasDocument()) {
            throw new ResourceNotFoundException("This cover letter has no document attached");
        }

        return new CoverLetterDocument(letter, storage.loadAsResource(letter.getStorageKey()));
    }

    @Transactional
    public void delete(Long userId, Long coverLetterId) {
        CoverLetter letter = coverLetterRepository.findByIdAndUserId(coverLetterId, userId)
                .orElseThrow(() -> ResourceNotFoundException.coverLetter(coverLetterId));

        coverLetterRepository.delete(letter);
        storage.delete(letter.getStorageKey());
    }

    private void attachDocument(CoverLetter letter, MultipartFile file) {
        String contentType = file.getContentType();
        String normalizedType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);

        if (!ALLOWED_CONTENT_TYPES.contains(normalizedType)) {
            throw new IllegalArgumentException("Only PDF and DOCX files are supported");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File size must be less than 10MB");
        }

        ResumeFileType fileType = normalizedType.equals(PDF) ? ResumeFileType.PDF : ResumeFileType.DOCX;

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read the uploaded file", ex);
        }

        validateMagicBytes(bytes, fileType);

        String originalFileName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename()
                : "cover-letter";

        letter.setFileName(originalFileName);
        letter.setContentType(normalizedType);
        letter.setFileType(fileType);
        letter.setFileSize((long) bytes.length);
        letter.setStorageKey(storage.store(bytes, originalFileName));
    }

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

    private Job resolveJob(Long userId, Long jobId) {
        if (jobId == null) {
            return null;
        }
        return jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> ResourceNotFoundException.job(jobId));
    }

    private String resolveName(String requestedName, CoverLetter letter) {
        if (StringUtils.hasText(requestedName)) {
            return requestedName.trim();
        }
        if (letter.getFileName() != null) {
            String fileName = letter.getFileName();
            int dot = fileName.lastIndexOf('.');
            return dot > 0 ? fileName.substring(0, dot) : fileName;
        }
        if (letter.getJob() != null) {
            return "Cover letter for " + letter.getJob().getJobTitle();
        }
        return "Cover letter";
    }

    private CoverLetterDto toDto(CoverLetter letter) {
        return new CoverLetterDto(
                letter.getId(),
                letter.getName(),
                letter.getJob() != null ? letter.getJob().getId() : null,
                letter.getBody(),
                letter.getFileName(),
                letter.getFileType(),
                letter.getFileSize(),
                letter.getVersionTag(),
                letter.getCreatedAt());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Metadata plus a streamable handle to the stored document. */
    public record CoverLetterDocument(CoverLetter coverLetter, Resource resource) {
    }
}

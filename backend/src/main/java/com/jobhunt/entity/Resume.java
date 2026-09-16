package com.jobhunt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * An uploaded resume/CV. The document bytes live in the database so the MVP needs no
 * separate file store; list endpoints map entities to DTOs that never expose them.
 */
@Entity
@Table(name = "resumes")
public class Resume {

    /** 10 MB ceiling, matching the limit enforced by the UI. */
    public static final int MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;

    /**
     * Declared column length for the stored document bytes. PostgreSQL maps this to an
     * unbounded {@code bytea} column, so it does not constrain real uploads; the value is
     * kept within H2's VARBINARY limit so the same mapping works in the test database.
     */
    public static final int FILE_DATA_COLUMN_LENGTH = 1_048_576;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", length = 150)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "file_type", nullable = false, length = 20)
    private ResumeFileType fileType;

    @Column(name = "version_tag", length = 200)
    private String versionTag;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    /** Key in the file storage backend. Null only for rows awaiting the legacy backfill. */
    @Column(name = "storage_key", length = 255)
    private String storageKey;

    // Legacy column: documents used to live in the database. It is kept nullable so the
    // startup backfill can move existing rows into storage without losing data.
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "file_data", length = FILE_DATA_COLUMN_LENGTH)
    private byte[] fileData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Resume() {
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public ResumeFileType getFileType() {
        return fileType;
    }

    public void setFileType(ResumeFileType fileType) {
        this.fileType = fileType;
    }

    public String getVersionTag() {
        return versionTag;
    }

    public void setVersionTag(String versionTag) {
        this.versionTag = versionTag;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public byte[] getFileData() {
        return fileData;
    }

    public void setFileData(byte[] fileData) {
        this.fileData = fileData;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

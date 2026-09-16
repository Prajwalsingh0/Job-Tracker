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
 * One recorded pipeline transition for a job. Written by the service whenever a job is
 * created or its status changes, which is what makes "recent activity" a real event log.
 */
@Entity
@Table(name = "job_status_history")
public class JobStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    /** Null when the job was first created. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "from_status", length = 30)
    private JobStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "to_status", nullable = false, length = 30)
    private JobStatus toStatus;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Column(name = "note", length = 500)
    private String note;

    public JobStatusHistory() {
    }

    public JobStatusHistory(Job job, JobStatus fromStatus, JobStatus toStatus, String note) {
        this.job = job;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        if (changedAt == null) {
            changedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Job getJob() {
        return job;
    }

    public void setJob(Job job) {
        this.job = job;
    }

    public JobStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(JobStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public JobStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(JobStatus toStatus) {
        this.toStatus = toStatus;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}

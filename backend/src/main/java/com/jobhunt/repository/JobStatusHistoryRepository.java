package com.jobhunt.repository;

import com.jobhunt.entity.JobStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobStatusHistoryRepository extends JpaRepository<JobStatusHistory, Long> {

    List<JobStatusHistory> findByJobIdOrderByChangedAtAsc(Long jobId);

    /** Most recent transitions across all of a user's jobs, for the dashboard activity feed. */
    List<JobStatusHistory> findTop20ByJob_UserIdOrderByChangedAtDesc(Long userId);
}

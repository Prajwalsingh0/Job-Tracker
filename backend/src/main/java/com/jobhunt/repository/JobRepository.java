package com.jobhunt.repository;

import com.jobhunt.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<Job> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    /** Job counts per resume, used to show "used in N applications" in the UI. */
    @Query("""
            select j.resume.id, count(j)
            from Job j
            where j.user.id = :userId and j.resume is not null
            group by j.resume.id
            """)
    List<Object[]> countJobsByResumeForUser(@Param("userId") Long userId);

    /** Detach a resume from every job before the resume itself is deleted. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Job j set j.resume = null where j.resume.id = :resumeId")
    void clearResumeReference(@Param("resumeId") Long resumeId);
}

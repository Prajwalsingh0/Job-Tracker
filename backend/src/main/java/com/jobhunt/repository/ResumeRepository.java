package com.jobhunt.repository;

import com.jobhunt.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {

    List<Resume> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Resume> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    /**
     * Rows that still hold their document in the database. Used once by the startup
     * backfill that moves legacy blobs into file storage.
     */
    List<Resume> findByStorageKeyIsNullAndFileDataIsNotNull();
}

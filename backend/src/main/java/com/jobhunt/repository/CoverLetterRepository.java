package com.jobhunt.repository;

import com.jobhunt.entity.CoverLetter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CoverLetterRepository extends JpaRepository<CoverLetter, Long> {

    List<CoverLetter> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<CoverLetter> findByIdAndUserId(Long id, Long userId);
}

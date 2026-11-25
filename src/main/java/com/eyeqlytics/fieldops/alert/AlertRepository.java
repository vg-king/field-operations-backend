package com.eyeqlytics.fieldops.alert;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    Page<Alert> findByTargetUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<Alert> findByTargetUserIdAndReadAtIsNullOrderByCreatedAtDesc(Long userId, Pageable pageable);
    long countByTargetUserIdAndReadAtIsNull(Long userId);
    Optional<Alert> findByIdAndTargetUserId(Long id, Long userId);
}

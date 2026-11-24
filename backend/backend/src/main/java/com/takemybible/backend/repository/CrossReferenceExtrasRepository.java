package com.takemybible.backend.repository;

import com.takemybible.backend.entity.cross_reference.CrossReferenceExtras;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CrossReferenceExtrasRepository extends JpaRepository<CrossReferenceExtras, Long> {
    Optional<CrossReferenceExtras> findByCrossReferenceId(Long crossReferenceId);
}

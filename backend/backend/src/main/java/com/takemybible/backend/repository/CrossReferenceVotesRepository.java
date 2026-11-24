package com.takemybible.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.takemybible.backend.entity.cross_reference.CrossReferenceVotes;
import com.takemybible.backend.entity.cross_reference.CrossReferenceVotesId;

public interface CrossReferenceVotesRepository extends JpaRepository<CrossReferenceVotes, CrossReferenceVotesId> {
    
    CrossReferenceVotes  findOneByUserIdAndCrossReferenceId(Long userId, Long crossRefenceId);
}

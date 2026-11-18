package com.takemybible.backend.controller.cross_reference;

import com.takemybible.backend.security.AuthenticatedUser;
import com.takemybible.backend.service.CrossReferenceService;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.takemybible.backend.entity.CrossReferenceVotes;
import com.takemybible.backend.repository.CrossReferenceVotesRepository;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/api/cross-reference/vote")
public class CrossReferenceVotesController {

    private final CrossReferenceService crossReferenceService;

    public CrossReferenceVotesController(CrossReferenceVotesRepository crossReferenceVotesRepository, CrossReferenceService crossReferenceService) {
        this.crossReferenceService = crossReferenceService;
    }

    // const response = await fetch(`${BACKEND_BASE_URL}api/cross-reference/vote/${reference.id}/${user.userId}/${voteValue}`, {

    /**
     * @param voteValue is always either 1 or -1 indicating up or down vote
     */
    @PostMapping("/{crossReferenceId}/{voteValue}")
    public ResponseEntity<Void> makeVote(
            @PathVariable Long crossReferenceId,
            @PathVariable Integer voteValue, @AuthenticationPrincipal AuthenticatedUser user) {

        if (voteValue > 1 || voteValue < -1) {
            return ResponseEntity.badRequest().build();
        }
        if (!crossReferenceService.exists(crossReferenceId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return crossReferenceService.vote(user.id(),  crossReferenceId, voteValue);
    }
}

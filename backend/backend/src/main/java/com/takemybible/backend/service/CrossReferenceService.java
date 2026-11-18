package com.takemybible.backend.service;


import com.takemybible.backend.dto.CrossReferenceDto;
import com.takemybible.backend.entity.CrossReferenceVotes;
import com.takemybible.backend.repository.CrossReferenceVotesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.takemybible.backend.dto.NewCrossReferenceInputDto;
import com.takemybible.backend.entity.CrossReference;
import com.takemybible.backend.entity.CrossReferenceExtras;
import com.takemybible.backend.repository.CrossReferenceExtrasRepository;
import com.takemybible.backend.repository.CrossReferenceRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class CrossReferenceService {
    private final CrossReferenceRepository crossReferenceRepository;
    private final CrossReferenceExtrasRepository crossReferenceExtrasRepository;
    private final CrossReferenceVotesRepository crossReferenceVotesRepository;
    private static final Logger log = LoggerFactory.getLogger(CrossReferenceService.class);

    public CrossReferenceService (CrossReferenceRepository crossReferenceRepository, CrossReferenceExtrasRepository crossReferenceExtrasRepository,  CrossReferenceVotesRepository crossReferenceVotesRepository) {
        this.crossReferenceRepository = crossReferenceRepository;
        this.crossReferenceExtrasRepository = crossReferenceExtrasRepository;
        this.crossReferenceVotesRepository = crossReferenceVotesRepository;
    }

    public void updateVotes(Long crossReferenceId, Integer value) {
        crossReferenceRepository.addVotes(crossReferenceId, value);
//        Optional<CrossReference> crossReference = crossReferenceRepository.findById(crossReferenceId);
//        crossReference.ifPresent(reference ->{
//            reference.setVotes(reference.getVotes() + value);
//            crossReferenceRepository.save(reference);
//        });

    }

    public boolean exists(Long crossReferenceId) {
        return crossReferenceRepository.existsById(crossReferenceId);
    }
    /**
     * Created references are unique by userId and toVerseStart and toVerseEnd
     * User can only have one reference per verse range
     * If they are the same then override the previous one
     */
    @Transactional
    public CrossReference create(NewCrossReferenceInputDto dto, Long userId) {
        log.debug("Creating or updating a cross reference");

        // single verse → end = start, so the unique constraint and lookup work
        int end = dto.toVerseEnd() != null ? dto.toVerseEnd() : dto.toVerseStart();

        Optional<CrossReference> existing = crossReferenceRepository.findDuplicate(
                userId, dto.fromBook(), dto.fromChapter(), dto.fromVerse(),
                dto.toBook(), dto.toChapter(), dto.toVerseStart(), end);

        if (existing.isPresent()) {
            CrossReference reference = existing.get();

            CrossReferenceExtras extras = crossReferenceExtrasRepository
                    .findByCrossReferenceId(reference.getId())
                    .orElseGet(() -> {
                        CrossReferenceExtras e = new CrossReferenceExtras();
                        e.setCrossReference(reference);
                        return e;
                    });

            applyExtras(extras, dto);
            crossReferenceExtrasRepository.save(extras);
            return reference; // votes and createdBy stay untouched
        }

        CrossReference crossReference = getCrossReference(dto, userId);
        crossReferenceRepository.save(crossReference);

        CrossReferenceExtras extras = new CrossReferenceExtras();
        extras.setCrossReference(crossReference);
        applyExtras(extras, dto);
        crossReferenceExtrasRepository.save(extras);

        return crossReference;
    }

    private static void applyExtras(CrossReferenceExtras extras, NewCrossReferenceInputDto dto) {
        extras.setConfidence(dto.confidence());
        extras.setConnectionType(dto.connectionType());
        extras.setThemeTags(dto.themeTags());
    }

    private static @NonNull CrossReference getCrossReference(NewCrossReferenceInputDto dto, Long userId) {
        CrossReference crossReference = new CrossReference();
        crossReference.setFromBook(dto.fromBook());
        crossReference.setToBook(dto.toBook());
        crossReference.setVotes(0);
        crossReference.setFromChapter(dto.fromChapter());
        crossReference.setToChapter(dto.toChapter());
        crossReference.setToVerse(dto.toVerse());
        crossReference.setToVerseEnd(dto.toVerseEnd());
        crossReference.setToVerseStart(dto.toVerseStart());
        crossReference.setFromVerse(dto.fromVerse());
        crossReference.setCreatedBy(userId);
        return crossReference;
    }

    @Transactional
    public ResponseEntity<Void> vote(Long userId, Long crossReferenceId, Integer voteValue) {

        CrossReferenceVotes existing =
                crossReferenceVotesRepository.findOneByUserIdAndCrossReferenceId(userId, crossReferenceId);

        if (voteValue == -1 ) {
            log.debug("making a down vote");
        } else {
            log.debug("making an up vote");
        }
        if (existing == null) {
            // New vote
            crossReferenceVotesRepository.save(new CrossReferenceVotes(userId, crossReferenceId, voteValue));
            updateVotes(crossReferenceId, voteValue);
            return ResponseEntity.status(HttpStatus.CREATED).build();
        }

        if (!existing.getValue().equals(voteValue)) {
            // Switching up <-> down
            existing.setValue(voteValue);
            crossReferenceVotesRepository.save(existing);
            updateVotes(crossReferenceId, voteValue * 2);
        } else {
            // Same vote again: undo it
            crossReferenceVotesRepository.delete(existing);
            updateVotes(crossReferenceId, -voteValue);
        }
        return ResponseEntity.noContent().build();
    }

    public ResponseEntity<List<CrossReferenceDto>> getCrossReferences(String bookName, Integer chapterNumber, Integer verse, Long userId) {

//        if (userId == null) {
//            // cross-references without userVotes
//            List<CrossReference> crossReferences = crossReferenceRepository.findByFromBookAndFromChapterAndFromVerse(bookName, chapterNumber, verse);
//            return ResponseEntity.ok(crossReferences.stream().map((crossReference -> new CrossReferenceDto(crossReference.getId(),
//                    crossReference.getFromBook(),
//                    crossReference.getFromChapter(),
//                    crossReference.getFromVerse(),
//                    crossReference.getToBook(),
//                    crossReference.getToChapter(),
//                    crossReference.getToVerseStart(),
//                    crossReference.getToVerseEnd(),
//                    crossReference.getVotes(),
//                    0
//            ))).toList());
//        }
        List<CrossReferenceDto> crossReferenceDto = crossReferenceRepository.findWithUserVote(bookName, chapterNumber, verse, userId);

        return  ResponseEntity.ok(crossReferenceDto);
    }
}

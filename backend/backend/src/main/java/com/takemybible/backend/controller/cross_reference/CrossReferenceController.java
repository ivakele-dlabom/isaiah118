package com.takemybible.backend.controller.cross_reference;

import com.takemybible.backend.dto.CrossReferenceDto;
import com.takemybible.backend.dto.NewCrossReferenceInputDto;
import com.takemybible.backend.security.AuthenticatedUser;
import com.takemybible.backend.service.CrossReferenceService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.takemybible.backend.entity.cross_reference.CrossReference;

import java.util.List;

@RestController
@RequestMapping("/api/kjv/cross-references")
public class CrossReferenceController {

    
    private final CrossReferenceService crossReferenceService;
    private final Logger logger =  LoggerFactory.getLogger(CrossReferenceController.class);
    public CrossReferenceController(  CrossReferenceService crossReferenceService) {
        this.crossReferenceService = crossReferenceService;
    }

    @PostMapping("/new")
    public ResponseEntity<CrossReference> newCrossReference(@Valid @RequestBody NewCrossReferenceInputDto newCrossReferenceInputDto, @AuthenticationPrincipal AuthenticatedUser user) {
        logger.debug("Creating a new cross reference");
        if (user != null) {
            logger.debug("User is logged in");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(crossReferenceService.create(newCrossReferenceInputDto, user.id()));
    }

    @GetMapping("/{bookName}/{chapterNumber}/{verse}")
    public ResponseEntity<List<CrossReferenceDto>> getCrossReferences(
            @PathVariable String bookName,
            @PathVariable Integer chapterNumber,
            @PathVariable Integer verse,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return crossReferenceService.getCrossReferences(bookName, chapterNumber, verse, user == null? null: user.id());

    }
}

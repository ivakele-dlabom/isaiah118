package com.takemybible.backend.controller.comment;

import com.takemybible.backend.repository.CommentRepository;
import com.takemybible.backend.repository.UserRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;

    public CommentController(CommentRepository commentRepository,
                             UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
    }

//    @PostMapping
//    public ResponseEntity<?> createComment(@RequestBody CommentRequest request,
//                                           @AuthenticationPrincipal AuthenticatedUser user) {
//        if (request.getBody() == null || request.getBody().isBlank()) {
//            return ResponseEntity.badRequest().body(Map.of("error", "Comment body is required"));
//        }
//        if (request.getVerseId() == null) {
//            return ResponseEntity.badRequest().body(Map.of("error", "Verse ID is required"));
//        }
//
//        Optional<BibleVerse> verseOpt = verseRepository.findById(user.id());
//        if (verseOpt.isEmpty()) {
//            return ResponseEntity.badRequest().body(Map.of("error", "Verse not found"));
//        }
//
//        Comment comment = new Comment();
//        comment.setAuthor(userOpt.get());
//        comment.setVerse(verseOpt.get());
//        comment.setBody(request.getBody().trim());
//
//        if (request.getParentCommentId() != null) {
//            Optional<Comment> parentOpt = commentRepository.findById(request.getParentCommentId());
//            parentOpt.ifPresent(comment::setParentComment);
//        }
//
//        comment = commentRepository.save(comment);
//
//        Map<String, Object> response = Map.of(
//                "id", comment.getId(),
//                "author", comment.getAuthor().getUsername(),
//                "body", comment.getBody(),
//                "likeCount", comment.getLikeCount(),
//                "createdAt", comment.getCreatedAt().toString()
//        );
//
//        return ResponseEntity.status(HttpStatus.CREATED).body(response);
//    }
//
//    @GetMapping("/verse/{verseId}")
//    public ResponseEntity<List<Map<String, Object>>> getCommentsByVerse(@PathVariable Long verseId) {
//        List<Comment> comments = commentRepository.findByVerseIdAndDeletedAtIsNullOrderByCreatedAtDesc(verseId);
//
//        List<Map<String, Object>> response = comments.stream().map(comment -> Map.<String, Object>of(
//                "id", comment.getId(),
//                "author", comment.getAuthor().getUsername(),
//                "body", comment.getBody(),
//                "likeCount", comment.getLikeCount(),
//                "createdAt", comment.getCreatedAt().toString()
//        )).toList();
//
//        return ResponseEntity.ok(response);
//    }
}

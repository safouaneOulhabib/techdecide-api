package com.techdecide.api.controller;

import com.techdecide.api.dto.comment.CommentDTO;
import com.techdecide.api.dto.comment.CreateCommentRequest;
import com.techdecide.api.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/api/decisions/{decisionId}/comments")
    public ResponseEntity<CommentDTO> create(
            @PathVariable Long decisionId,
            @Valid @RequestBody CreateCommentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(commentService.create(decisionId, request, userDetails.getUsername()));
    }

    @GetMapping("/api/decisions/{decisionId}/comments")
    public ResponseEntity<List<CommentDTO>> getByDecision(
            @PathVariable Long decisionId) {
        return ResponseEntity.ok(commentService.getByDecision(decisionId));
    }

    @DeleteMapping("/api/comments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

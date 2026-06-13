package com.techdecide.api.controller;

import com.techdecide.api.dto.decision.CreateDecisionRequest;
import com.techdecide.api.dto.decision.DecisionDTO;
import com.techdecide.api.dto.decision.UpdateDecisionRequest;
import com.techdecide.api.entity.Decision;
import com.techdecide.api.service.DecisionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/decisions")
@RequiredArgsConstructor
public class DecisionController {

    private final DecisionService decisionService;

    @PostMapping
    public ResponseEntity<DecisionDTO> create(
            @Valid @RequestBody CreateDecisionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(decisionService.create(request, userDetails.getUsername()));
    }

    @GetMapping
    public ResponseEntity<List<DecisionDTO>> getAll(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(decisionService.getAll(userDetails.getUsername()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DecisionDTO> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(decisionService.getById(id, userDetails.getUsername()));
    }

    @GetMapping("/team/{teamId}")
    public ResponseEntity<List<DecisionDTO>> getByTeam(
            @PathVariable Long teamId) {
        return ResponseEntity.ok(decisionService.getByTeam(teamId));
    }

    @GetMapping("/search")
    public ResponseEntity<List<DecisionDTO>> search(
            @RequestParam String keyword) {
        return ResponseEntity.ok(decisionService.search(keyword));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DecisionDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDecisionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(decisionService.update(id, request, userDetails.getUsername()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<DecisionDTO> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        Decision.Status status = Decision.Status.valueOf(body.get("status"));
        Long supersededById = body.containsKey("supersededById")
                ? Long.parseLong(body.get("supersededById"))
                : null;
        return ResponseEntity.ok(decisionService.updateStatus(id, status, supersededById, userDetails.getUsername()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        decisionService.delete(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}

package com.techdecide.api.controller;

import com.techdecide.api.dto.tag.CreateTagRequest;
import com.techdecide.api.dto.tag.TagDTO;
import com.techdecide.api.service.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @PostMapping
    public ResponseEntity<TagDTO> create(
            @Valid @RequestBody CreateTagRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(tagService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<TagDTO>> getAll() {
        return ResponseEntity.ok(tagService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TagDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(tagService.getById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

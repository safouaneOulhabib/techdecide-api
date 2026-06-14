package com.techdecide.api.service;

import com.techdecide.api.dto.tag.CreateTagRequest;
import com.techdecide.api.dto.tag.TagDTO;
import com.techdecide.api.entity.Tag;
import com.techdecide.api.entity.User;
import com.techdecide.api.exception.ConflictException;
import com.techdecide.api.exception.ForbiddenException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.repository.TagRepository;
import com.techdecide.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TagService {

    private final TagRepository tagRepository;
    private final UserRepository userRepository;

    public TagDTO create(CreateTagRequest request, String actorEmail) {
        requireAppAdmin(actorEmail);
        if (tagRepository.existsByName(request.getName())) {
            throw new ConflictException("Tag with name '" + request.getName() + "' already exists");
        }

        Tag tag = Tag.builder()
                .name(request.getName())
                .color(request.getColor())
                .build();

        Tag saved = tagRepository.save(tag);
        return mapToDTO(saved);
    }

    public List<TagDTO> getAll() {
        return tagRepository.findAll()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public TagDTO getById(Long id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tag", id));
        return mapToDTO(tag);
    }

    public void delete(Long id, String actorEmail) {
        requireAppAdmin(actorEmail);
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tag", id));
        tagRepository.delete(tag);
    }

    private void requireAppAdmin(String actorEmail) {
        User actor = userRepository.findByEmail(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!"APP_ADMIN".equals(actor.getAppRole())) {
            throw new ForbiddenException("Only APP_ADMIN can manage tags");
        }
    }

    private TagDTO mapToDTO(Tag tag) {
        return TagDTO.builder()
                .id(tag.getId())
                .name(tag.getName())
                .color(tag.getColor())
                .build();
    }
}

package com.techdecide.api.service;

import com.techdecide.api.dto.tag.CreateTagRequest;
import com.techdecide.api.dto.tag.TagDTO;
import com.techdecide.api.entity.Tag;
import com.techdecide.api.exception.ConflictException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock private TagRepository tagRepository;
    @InjectMocks private TagService tagService;

    private Tag buildTag() {
        return Tag.builder().id(1L).name("backend").color("#ff0000").build();
    }

    private CreateTagRequest buildRequest() {
        CreateTagRequest req = new CreateTagRequest();
        req.setName("backend");
        req.setColor("#ff0000");
        return req;
    }

    // --- create ---

    @Test
    void create_validRequest_returnsTagDTO() {
        when(tagRepository.existsByName("backend")).thenReturn(false);
        when(tagRepository.save(any())).thenReturn(buildTag());

        TagDTO result = tagService.create(buildRequest());

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("backend");
        assertThat(result.getColor()).isEqualTo("#ff0000");
    }

    @Test
    void create_duplicateName_throwsConflictException() {
        when(tagRepository.existsByName("backend")).thenReturn(true);

        assertThrows(ConflictException.class, () -> tagService.create(buildRequest()));
        verify(tagRepository, never()).save(any());
    }

    @Test
    void create_savesTagWithCorrectFields() {
        when(tagRepository.existsByName(any())).thenReturn(false);
        when(tagRepository.save(any())).thenReturn(buildTag());

        tagService.create(buildRequest());

        verify(tagRepository).save(argThat(t ->
                t.getName().equals("backend") && t.getColor().equals("#ff0000")));
    }

    @Test
    void create_withNullColor_savesWithNullColor() {
        CreateTagRequest req = new CreateTagRequest();
        req.setName("frontend");
        Tag tag = Tag.builder().id(2L).name("frontend").color(null).build();
        when(tagRepository.existsByName("frontend")).thenReturn(false);
        when(tagRepository.save(any())).thenReturn(tag);

        TagDTO result = tagService.create(req);

        assertThat(result.getColor()).isNull();
    }

    // --- getAll ---

    @Test
    void getAll_returnsAllTagsMapped() {
        Tag t2 = Tag.builder().id(2L).name("frontend").color("#00ff00").build();
        when(tagRepository.findAll()).thenReturn(List.of(buildTag(), t2));

        List<TagDTO> result = tagService.getAll();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(TagDTO::getName)
                .containsExactlyInAnyOrder("backend", "frontend");
    }

    @Test
    void getAll_emptyRepository_returnsEmptyList() {
        when(tagRepository.findAll()).thenReturn(List.of());

        assertThat(tagService.getAll()).isEmpty();
    }

    // --- getById ---

    @Test
    void getById_existingId_returnsTagDTO() {
        when(tagRepository.findById(1L)).thenReturn(Optional.of(buildTag()));

        TagDTO result = tagService.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("backend");
    }

    @Test
    void getById_nonExistingId_throwsResourceNotFoundException() {
        when(tagRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> tagService.getById(99L));
    }

    // --- delete ---

    @Test
    void delete_existingId_callsRepositoryDelete() {
        Tag existing = buildTag();
        when(tagRepository.findById(1L)).thenReturn(Optional.of(existing));

        tagService.delete(1L);

        verify(tagRepository).delete(existing);
    }

    @Test
    void delete_nonExistingId_throwsResourceNotFoundException() {
        when(tagRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> tagService.delete(99L));
        verify(tagRepository, never()).delete(any());
    }
}

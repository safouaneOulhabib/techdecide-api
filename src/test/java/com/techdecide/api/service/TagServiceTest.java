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
    @Mock private UserRepository userRepository;
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

    private User buildAdmin() {
        return User.builder().id(1L).name("Admin").email("admin@example.com")
                .password("pw").appRole("APP_ADMIN").build();
    }

    private User buildRegularUser() {
        return User.builder().id(2L).name("Alice").email("alice@example.com")
                .password("pw").appRole("USER").build();
    }

    // --- create ---

    @Test
    void create_appAdmin_returnsTagDTO() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAdmin()));
        when(tagRepository.existsByName("backend")).thenReturn(false);
        when(tagRepository.save(any())).thenReturn(buildTag());

        TagDTO result = tagService.create(buildRequest(), "admin@example.com");

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("backend");
        assertThat(result.getColor()).isEqualTo("#ff0000");
    }

    @Test
    void create_memberRole_throwsForbiddenException() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(buildRegularUser()));

        assertThrows(ForbiddenException.class, () -> tagService.create(buildRequest(), "alice@example.com"));
        verify(tagRepository, never()).save(any());
    }

    @Test
    void create_teamAdminAppRoleUser_throwsForbiddenException() {
        User teamAdmin = User.builder().id(3L).name("TeamAdmin").email("teamadmin@example.com")
                .password("pw").appRole("USER").build();
        when(userRepository.findByEmail("teamadmin@example.com")).thenReturn(Optional.of(teamAdmin));

        assertThrows(ForbiddenException.class, () -> tagService.create(buildRequest(), "teamadmin@example.com"));
        verify(tagRepository, never()).save(any());
    }

    @Test
    void create_duplicateName_throwsConflictException() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAdmin()));
        when(tagRepository.existsByName("backend")).thenReturn(true);

        assertThrows(ConflictException.class, () -> tagService.create(buildRequest(), "admin@example.com"));
        verify(tagRepository, never()).save(any());
    }

    @Test
    void create_savesTagWithCorrectFields() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAdmin()));
        when(tagRepository.existsByName(any())).thenReturn(false);
        when(tagRepository.save(any())).thenReturn(buildTag());

        tagService.create(buildRequest(), "admin@example.com");

        verify(tagRepository).save(argThat(t ->
                t.getName().equals("backend") && t.getColor().equals("#ff0000")));
    }

    @Test
    void create_withNullColor_savesWithNullColor() {
        CreateTagRequest req = new CreateTagRequest();
        req.setName("frontend");
        Tag tag = Tag.builder().id(2L).name("frontend").color(null).build();
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAdmin()));
        when(tagRepository.existsByName("frontend")).thenReturn(false);
        when(tagRepository.save(any())).thenReturn(tag);

        TagDTO result = tagService.create(req, "admin@example.com");

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
    void delete_appAdmin_callsRepositoryDelete() {
        Tag existing = buildTag();
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAdmin()));
        when(tagRepository.findById(1L)).thenReturn(Optional.of(existing));

        tagService.delete(1L, "admin@example.com");

        verify(tagRepository).delete(existing);
    }

    @Test
    void delete_memberRole_throwsForbiddenException() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(buildRegularUser()));

        assertThrows(ForbiddenException.class, () -> tagService.delete(1L, "alice@example.com"));
        verify(tagRepository, never()).delete(any());
    }

    @Test
    void delete_nonExistingId_throwsResourceNotFoundException() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAdmin()));
        when(tagRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> tagService.delete(99L, "admin@example.com"));
        verify(tagRepository, never()).delete(any());
    }
}

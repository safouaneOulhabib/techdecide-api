package com.techdecide.api.service;

import com.techdecide.api.dto.organization.CreateOrganizationRequest;
import com.techdecide.api.dto.organization.OrganizationDTO;
import com.techdecide.api.dto.organization.UpdateOrganizationRequest;
import com.techdecide.api.entity.Organization;
import com.techdecide.api.exception.ConflictException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock private OrganizationRepository organizationRepository;
    @InjectMocks private OrganizationService organizationService;

    private Organization buildOrg() {
        return Organization.builder()
                .id(1L).name("Acme").description("Tech company")
                .createdAt(LocalDateTime.now()).build();
    }

    private CreateOrganizationRequest buildRequest(String name) {
        CreateOrganizationRequest req = new CreateOrganizationRequest();
        req.setName(name);
        req.setDescription("A description");
        return req;
    }

    // --- create ---

    @Test
    void create_validRequest_returnsOrganizationDTO() {
        CreateOrganizationRequest req = buildRequest("Acme");
        when(organizationRepository.existsByName("Acme")).thenReturn(false);
        when(organizationRepository.save(any())).thenReturn(buildOrg());

        OrganizationDTO result = organizationService.create(req);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Acme");
        assertThat(result.getDescription()).isEqualTo("Tech company");
    }

    @Test
    void create_duplicateName_throwsConflictException() {
        CreateOrganizationRequest req = buildRequest("Acme");
        when(organizationRepository.existsByName("Acme")).thenReturn(true);

        assertThrows(ConflictException.class, () -> organizationService.create(req));
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void create_savesOrganizationWithCorrectFields() {
        CreateOrganizationRequest req = buildRequest("NewOrg");
        req.setDescription("Desc");
        when(organizationRepository.existsByName(any())).thenReturn(false);
        when(organizationRepository.save(any())).thenReturn(buildOrg());

        organizationService.create(req);

        verify(organizationRepository).save(argThat(o ->
                o.getName().equals("NewOrg") && o.getDescription().equals("Desc")));
    }

    // --- getAll ---

    @Test
    void getAll_returnsAllOrganizationsMapped() {
        Organization o2 = Organization.builder().id(2L).name("Beta")
                .description("Another").createdAt(LocalDateTime.now()).build();
        when(organizationRepository.findAll()).thenReturn(List.of(buildOrg(), o2));

        List<OrganizationDTO> result = organizationService.getAll();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(OrganizationDTO::getName)
                .containsExactlyInAnyOrder("Acme", "Beta");
    }

    @Test
    void getAll_emptyRepository_returnsEmptyList() {
        when(organizationRepository.findAll()).thenReturn(List.of());

        List<OrganizationDTO> result = organizationService.getAll();

        assertThat(result).isEmpty();
    }

    // --- getById ---

    @Test
    void getById_existingId_returnsOrganizationDTO() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(buildOrg()));

        OrganizationDTO result = organizationService.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Acme");
    }

    @Test
    void getById_nonExistingId_throwsResourceNotFoundException() {
        when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> organizationService.getById(99L));
    }

    // --- update ---

    @Test
    void update_validRequest_updatesAndReturnsDTO() {
        Organization existing = buildOrg();
        UpdateOrganizationRequest req = new UpdateOrganizationRequest();
        req.setName("Updated Acme");
        req.setDescription("A description");
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(organizationRepository.existsByName("Updated Acme")).thenReturn(false);
        when(organizationRepository.save(any())).thenReturn(existing);

        OrganizationDTO result = organizationService.update(1L, req);

        assertThat(result).isNotNull();
        verify(organizationRepository).save(existing);
    }

    @Test
    void update_nonExistingId_throwsResourceNotFoundException() {
        UpdateOrganizationRequest req = new UpdateOrganizationRequest();
        req.setName("Name");
        when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> organizationService.update(99L, req));
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void update_duplicateNameOnDifferentOrg_throwsConflictException() {
        Organization existing = buildOrg();
        existing.setName("Acme");
        UpdateOrganizationRequest req = new UpdateOrganizationRequest();
        req.setName("Beta");
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(organizationRepository.existsByName("Beta")).thenReturn(true);

        assertThrows(ConflictException.class, () -> organizationService.update(1L, req));
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void update_sameNameAsCurrentOrg_doesNotThrowConflict() {
        Organization existing = buildOrg();
        existing.setName("Acme");
        UpdateOrganizationRequest req = new UpdateOrganizationRequest();
        req.setName("Acme");
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(organizationRepository.save(any())).thenReturn(existing);

        OrganizationDTO result = organizationService.update(1L, req);

        assertThat(result).isNotNull();
        verify(organizationRepository, never()).existsByName(any());
    }

    // --- delete ---

    @Test
    void delete_existingId_callsRepositoryDelete() {
        Organization existing = buildOrg();
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(existing));

        organizationService.delete(1L);

        verify(organizationRepository).delete(existing);
    }

    @Test
    void delete_nonExistingId_throwsResourceNotFoundException() {
        when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> organizationService.delete(99L));
        verify(organizationRepository, never()).delete(any());
    }
}

package com.techdecide.api.service;

import com.techdecide.api.dto.organization.CreateOrganizationRequest;
import com.techdecide.api.dto.organization.OrganizationDTO;
import com.techdecide.api.dto.organization.UpdateOrganizationRequest;
import com.techdecide.api.entity.Organization;
import com.techdecide.api.exception.ConflictException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public OrganizationDTO create(CreateOrganizationRequest request) {
        if (organizationRepository.existsByName(request.getName())) {
            throw new ConflictException("Organization with name '" + request.getName() + "' already exists");
        }

        Organization organization = Organization.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();

        Organization saved = organizationRepository.save(organization);
        return mapToDTO(saved);
    }

    public List<OrganizationDTO> getAll() {
        return organizationRepository.findAll()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public OrganizationDTO getById(Long id) {
        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", id));
        return mapToDTO(organization);
    }

    public OrganizationDTO update(Long id, UpdateOrganizationRequest request) {
        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", id));

        if (request.getName() != null
                && !organization.getName().equals(request.getName())
                && organizationRepository.existsByName(request.getName())) {
            throw new ConflictException("Organization with name '" + request.getName() + "' already exists");
        }

        if (request.getName() != null) organization.setName(request.getName());
        if (request.getDescription() != null) organization.setDescription(request.getDescription());

        Organization updated = organizationRepository.save(organization);
        return mapToDTO(updated);
    }

    public void delete(Long id) {
        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", id));
        organizationRepository.delete(organization);
    }

    private OrganizationDTO mapToDTO(Organization organization) {
        return OrganizationDTO.builder()
                .id(organization.getId())
                .name(organization.getName())
                .description(organization.getDescription())
                .createdAt(organization.getCreatedAt())
                .build();
    }
}

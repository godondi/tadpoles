package com.neueda.leap.controller;

import com.neueda.leap.domain.Advisor;
import com.neueda.leap.dto.AdvisorListResponseDto;
import com.neueda.leap.dto.AdvisorResponseDto;
import com.neueda.leap.dto.ClientListResponseDto;
import com.neueda.leap.dto.CreateAdvisorRequestDto;
import com.neueda.leap.dto.UpdateAdvisorRequestDto;
import com.neueda.leap.service.AdvisorService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/advisors")
public class AdvisorController {
    private final AdvisorService advisorService;

    public AdvisorController(AdvisorService advisorService) {
        this.advisorService = advisorService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'ANALYST')")
    public AdvisorListResponseDto listAdvisors() {
        return AdvisorListResponseDto.fromEntities(advisorService.listAdvisors());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public AdvisorResponseDto createAdvisor(@RequestBody CreateAdvisorRequestDto request) {
        Advisor advisor = advisorService.createAdvisor(request);
        return AdvisorResponseDto.fromEntity(advisor);
    }

    @GetMapping("/{advisorId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'ANALYST', 'ADVISOR')")
    public AdvisorResponseDto getAdvisor(@PathVariable Integer advisorId) {
        Advisor advisor = advisorService.getAdvisor(advisorId);
        return AdvisorResponseDto.fromEntity(advisor);
    }

    @PatchMapping("/{advisorId}")
    @PreAuthorize("hasRole('ADMIN')")
    public AdvisorResponseDto updateAdvisor(
            @PathVariable Integer advisorId,
            @RequestBody UpdateAdvisorRequestDto request
    ) {
        Advisor advisor = advisorService.updateAdvisor(advisorId, request);
        return AdvisorResponseDto.fromEntity(advisor);
    }

    @GetMapping("/{advisorId}/clients")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADVISOR')")
    public ClientListResponseDto listAdvisorClients(@PathVariable Integer advisorId) {
        return ClientListResponseDto.fromEntities(advisorService.listAdvisorClients(advisorId));
    }
}

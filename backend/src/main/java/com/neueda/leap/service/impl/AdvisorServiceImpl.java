package com.neueda.leap.service.impl;

import com.neueda.leap.domain.Advisor;
import com.neueda.leap.domain.Client;
import com.neueda.leap.dto.CreateAdvisorRequestDto;
import com.neueda.leap.dto.UpdateAdvisorRequestDto;
import com.neueda.leap.exception.AdvisorNotFoundException;
import com.neueda.leap.mapper.AdvisorMapper;
import com.neueda.leap.mapper.ClientMapper;
import com.neueda.leap.service.AdvisorService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AdvisorServiceImpl implements AdvisorService {
    private final AdvisorMapper advisorMapper;
    private final ClientMapper clientMapper;

    public AdvisorServiceImpl(AdvisorMapper advisorMapper, ClientMapper clientMapper) {
        this.advisorMapper = advisorMapper;
        this.clientMapper = clientMapper;
    }

    @Override
    public Advisor getAdvisor(Integer id) {
        validateId(id);

        Advisor advisor = advisorMapper.getAdvisor(id);
        if (advisor == null) {
            throw new AdvisorNotFoundException(id);
        }

        return advisor;
    }

    @Override
    public List<Advisor> listAdvisors() {
        return advisorMapper.listAdvisors();
    }

    @Override
    public Advisor createAdvisor(CreateAdvisorRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Advisor request is required.");
        }
        if (request.advisorName() == null || request.advisorName().isBlank()) {
            throw new IllegalArgumentException("Advisor name is required.");
        }
        validateOptionalPositiveId(request.userId(), "User id must be a positive integer.");

        Advisor advisor = new Advisor();
        advisor.setAdvisorName(request.advisorName().trim());
        advisor.setUserId(request.userId());

        advisorMapper.insertAdvisor(advisor);
        return getAdvisor(advisor.getAdvisorId());
    }

    @Override
    public Advisor updateAdvisor(Integer id, UpdateAdvisorRequestDto request) {
        validateId(id);
        if (request == null) {
            throw new IllegalArgumentException("Advisor update request is required.");
        }
        if (request.advisorName() != null && request.advisorName().isBlank()) {
            throw new IllegalArgumentException("Advisor name cannot be blank.");
        }
        validateOptionalPositiveId(request.userId(), "User id must be a positive integer.");
        if (request.advisorName() == null && request.userId() == null) {
            throw new IllegalArgumentException("At least one field must be provided for update.");
        }

        Advisor update = new Advisor();
        update.setAdvisorId(id);
        update.setAdvisorName(request.advisorName() == null ? null : request.advisorName().trim());
        update.setUserId(request.userId());

        int rows = advisorMapper.updateAdvisor(update);
        if (rows == 0) {
            throw new AdvisorNotFoundException(id);
        }

        return getAdvisor(id);
    }

    @Override
    public List<Client> listAdvisorClients(Integer advisorId) {
        validateId(advisorId);
        getAdvisor(advisorId);
        return clientMapper.listClientsByAdvisor(advisorId);
    }

    private void validateId(Integer id) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException("Advisor id must be a positive integer.");
        }
    }

    private void validateOptionalPositiveId(Integer id, String message) {
        if (id != null && id < 1) {
            throw new IllegalArgumentException(message);
        }
    }
}

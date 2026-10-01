package com.neueda.leap.service;

import com.neueda.leap.domain.Advisor;
import com.neueda.leap.domain.Client;
import com.neueda.leap.dto.CreateAdvisorRequestDto;
import com.neueda.leap.dto.UpdateAdvisorRequestDto;
import java.util.List;

public interface AdvisorService {
    Advisor getAdvisor(Integer id);
    List<Advisor> listAdvisors();
    Advisor createAdvisor(CreateAdvisorRequestDto request);
    Advisor updateAdvisor(Integer id, UpdateAdvisorRequestDto request);
    List<Client> listAdvisorClients(Integer advisorId);
}

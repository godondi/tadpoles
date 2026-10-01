package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neueda.leap.domain.Advisor;
import com.neueda.leap.domain.Client;
import com.neueda.leap.dto.CreateAdvisorRequestDto;
import com.neueda.leap.dto.UpdateAdvisorRequestDto;
import com.neueda.leap.exception.AdvisorNotFoundException;
import com.neueda.leap.mapper.AdvisorMapper;
import com.neueda.leap.mapper.ClientMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdvisorServiceImplTest {
    @Mock
    private AdvisorMapper advisorMapper;
    @Mock
    private ClientMapper clientMapper;

    private AdvisorServiceImpl advisorService;

    @BeforeEach
    void setUp() {
        advisorService = new AdvisorServiceImpl(advisorMapper, clientMapper);
    }

    @Test
    void getAdvisorReturnsAdvisorWhenFound() {
        when(advisorMapper.getAdvisor(3)).thenReturn(buildAdvisor());

        Advisor result = advisorService.getAdvisor(3);

        assertEquals(3, result.getAdvisorId());
        assertEquals("Advisor One", result.getAdvisorName());
    }

    @Test
    void listAdvisorsReturnsAdvisors() {
        when(advisorMapper.listAdvisors()).thenReturn(List.of(buildAdvisor()));

        List<Advisor> results = advisorService.listAdvisors();

        assertEquals(1, results.size());
        assertEquals(3, results.get(0).getAdvisorId());
    }

    @Test
    void createAdvisorTrimsNameAndReturnsInsertedAdvisor() {
        CreateAdvisorRequestDto request = new CreateAdvisorRequestDto("  Advisor One  ", 1);

        when(advisorMapper.insertAdvisor(any())).thenAnswer(invocation -> {
            Advisor toInsert = invocation.getArgument(0, Advisor.class);
            toInsert.setAdvisorId(3);
            return 1;
        });
        when(advisorMapper.getAdvisor(3)).thenReturn(buildAdvisor());

        Advisor result = advisorService.createAdvisor(request);

        ArgumentCaptor<Advisor> captor = ArgumentCaptor.forClass(Advisor.class);
        verify(advisorMapper).insertAdvisor(captor.capture());
        assertEquals("Advisor One", captor.getValue().getAdvisorName());
        assertEquals(1, captor.getValue().getUserId());
        assertEquals(3, result.getAdvisorId());
    }

    @Test
    void updateAdvisorReturnsUpdatedAdvisor() {
        UpdateAdvisorRequestDto request = new UpdateAdvisorRequestDto("Advisor Updated", 2);
        Advisor updated = buildAdvisor();
        updated.setAdvisorName("Advisor Updated");
        updated.setUserId(2);

        when(advisorMapper.updateAdvisor(any())).thenReturn(1);
        when(advisorMapper.getAdvisor(3)).thenReturn(updated);

        Advisor result = advisorService.updateAdvisor(3, request);

        assertEquals("Advisor Updated", result.getAdvisorName());
        assertEquals(2, result.getUserId());
    }

    @Test
    void listAdvisorClientsReturnsAssignedClients() {
        when(advisorMapper.getAdvisor(3)).thenReturn(buildAdvisor());
        when(clientMapper.listClientsByAdvisor(3)).thenReturn(List.of(buildClient()));

        List<Client> results = advisorService.listAdvisorClients(3);

        assertEquals(1, results.size());
        assertEquals(7, results.get(0).getClientId());
    }

    @Test
    void getAdvisorThrowsWhenMissing() {
        when(advisorMapper.getAdvisor(99)).thenReturn(null);
        assertThrows(AdvisorNotFoundException.class, () -> advisorService.getAdvisor(99));
    }

    @Test
    void updateAdvisorThrowsWhenMissing() {
        when(advisorMapper.updateAdvisor(any())).thenReturn(0);
        assertThrows(AdvisorNotFoundException.class,
                () -> advisorService.updateAdvisor(99, new UpdateAdvisorRequestDto("A", null)));
    }

    @Test
    void getAdvisorThrowsOnInvalidId() {
        assertThrows(IllegalArgumentException.class, () -> advisorService.getAdvisor(0));
    }

    @Test
    void createAdvisorThrowsOnBlankName() {
        CreateAdvisorRequestDto request = new CreateAdvisorRequestDto(" ", null);
        assertThrows(IllegalArgumentException.class, () -> advisorService.createAdvisor(request));
    }

    @Test
    void createAdvisorThrowsOnInvalidUserId() {
        CreateAdvisorRequestDto request = new CreateAdvisorRequestDto("Advisor One", 0);
        assertThrows(IllegalArgumentException.class, () -> advisorService.createAdvisor(request));
    }

    @Test
    void updateAdvisorThrowsWhenNoFieldsProvided() {
        UpdateAdvisorRequestDto request = new UpdateAdvisorRequestDto(null, null);
        assertThrows(IllegalArgumentException.class, () -> advisorService.updateAdvisor(3, request));
    }

    private Advisor buildAdvisor() {
        Advisor advisor = new Advisor();
        advisor.setAdvisorId(3);
        advisor.setAdvisorName("Advisor One");
        advisor.setUserId(1);
        return advisor;
    }

    private Client buildClient() {
        Client client = new Client();
        client.setClientId(7);
        client.setClientName("Alice Investor");
        client.setAdvisorId(3);
        client.setModelPortfolioId(5);
        client.setCashBalance(new BigDecimal("1200.50"));
        return client;
    }
}

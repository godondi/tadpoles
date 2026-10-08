package com.neueda.leap.service;

import com.neueda.leap.domain.AppUser;
import com.neueda.leap.dto.ClientOnboardingResponseDto;
import com.neueda.leap.dto.CompleteClientOnboardingRequestDto;

public interface ClientOnboardingService {
    ClientOnboardingResponseDto getCurrentProfile(AppUser currentUser);
    ClientOnboardingResponseDto completeOnboarding(AppUser currentUser, CompleteClientOnboardingRequestDto request);
}


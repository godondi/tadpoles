package com.neueda.leap.service;

import com.neueda.leap.dto.CreateUserRequestDto;
import com.neueda.leap.dto.CreateUserResponseDto;
import com.neueda.leap.dto.ClientRegistrationRequestDto;
import com.neueda.leap.dto.ClientRegistrationResponseDto;

public interface UserService {
    CreateUserResponseDto registerUser(CreateUserRequestDto request);
    ClientRegistrationResponseDto registerClient(ClientRegistrationRequestDto request);
}

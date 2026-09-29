package com.neueda.leap.service;

import com.neueda.leap.dto.CreateUserRequestDto;
import com.neueda.leap.dto.CreateUserResponseDto;

public interface UserService {
    CreateUserResponseDto registerUser(CreateUserRequestDto request);
}


package com.neueda.leap.service;

import com.neueda.leap.dto.LoginRequestDto;
import com.neueda.leap.dto.LoginResponseDto;

public interface AuthService {
    LoginResponseDto login(LoginRequestDto request);
}


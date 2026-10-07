package com.neueda.leap.service;

import com.neueda.leap.dto.AuthTokensResponseDto;
import com.neueda.leap.dto.LoginRequestDto;

public interface AuthService {
    AuthTokensResponseDto login(LoginRequestDto request);
}

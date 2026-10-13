package com.neueda.leap.service;

import com.neueda.leap.dto.LoginRequestDto;
import com.neueda.leap.dto.LoginResponseDto;
import com.neueda.leap.dto.SignupRequestDto;
import com.neueda.leap.dto.SignupResponseDto;

public interface AuthService {
    LoginResponseDto login(LoginRequestDto request);
    SignupResponseDto signup(SignupRequestDto request);
}


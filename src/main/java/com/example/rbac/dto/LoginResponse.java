package com.example.rbac.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 로그인 응답 DTO */
@Getter
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private String role;
    private String username;

    public static LoginResponse of(String accessToken, String refreshToken, String role, String username) {
        return new LoginResponse(accessToken, refreshToken, "Bearer", role, username);
    }
}

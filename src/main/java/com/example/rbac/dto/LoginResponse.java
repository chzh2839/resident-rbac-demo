package com.example.rbac.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 로그인 응답 DTO */
@Getter
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String tokenType;
    private String role;
    private String username;

    public static LoginResponse of(String token, String role, String username) {
        return new LoginResponse(token, "Bearer", role, username);
    }
}

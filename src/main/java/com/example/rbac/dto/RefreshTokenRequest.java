package com.example.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

/** Refresh Token 재발급 요청 DTO */
@Getter
public class RefreshTokenRequest {

    @NotBlank(message = "refreshToken은 필수입니다")
    private String refreshToken;
}

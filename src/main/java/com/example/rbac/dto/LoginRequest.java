package com.example.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

/** 로그인 요청 DTO */
@Getter
public class LoginRequest {

    @NotBlank(message = "username은 필수입니다")
    private String username;

    @NotBlank(message = "password는 필수입니다")
    private String password;
}

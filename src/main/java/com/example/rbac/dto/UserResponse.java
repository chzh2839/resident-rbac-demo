package com.example.rbac.dto;

import com.example.rbac.entity.Role;
import com.example.rbac.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/** 사용자 응답 DTO - 비밀번호 등 민감 정보 제외 */
@Getter
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String username;
    private String name;
    private String email;
    private Role role;
    private String unitNumber;
    private LocalDateTime createdAt;

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getUnitNumber(),
                user.getCreatedAt()
        );
    }
}

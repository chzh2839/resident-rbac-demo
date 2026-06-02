package com.example.rbac.dto;

import com.example.rbac.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 거주자 프로필 응답 DTO */
@Getter
@AllArgsConstructor
public class ResidentProfileResponse {

    private Long id;
    private String name;
    private String email;
    private String unitNumber;  // 동/호수

    public static ResidentProfileResponse from(User user) {
        return new ResidentProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getUnitNumber()
        );
    }
}

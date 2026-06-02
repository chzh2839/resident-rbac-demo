package com.example.rbac.controller;

import com.example.rbac.dto.ResidentProfileResponse;
import com.example.rbac.service.ResidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * 거주자(Resident) 컨트롤러
 *
 * GET /api/resident/profile : 거주자 본인 프로필 조회
 *
 * 고려사항:
 * - @AuthenticationPrincipal 로 SecurityContext 에서 현재 인증 사용자 주입
 * - 서비스 계층에서 username 기반으로 본인 데이터만 조회 (IDOR 방어)
 */
@RestController
@RequestMapping("/api/resident")
@RequiredArgsConstructor
public class ResidentController {

    private final ResidentService residentService;

    /**
     * GET /api/resident/profile
     * 본인 프로필 조회 (RESIDENT 전용)
     *
     * ADMIN 이 이 API 를 호출할 필요가 없도록 역할을 RESIDENT 로 제한.
     * 운영자가 특정 거주자 정보를 조회하려면 /api/admin/users/{id} 를 사용.
     */
    @GetMapping("/profile")
    @PreAuthorize("hasRole('RESIDENT')")
    public ResponseEntity<ResidentProfileResponse> getMyProfile(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(residentService.getMyProfile(userDetails.getUsername()));
    }
}

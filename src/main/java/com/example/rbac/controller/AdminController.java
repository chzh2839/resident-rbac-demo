package com.example.rbac.controller;

import com.example.rbac.dto.UserResponse;
import com.example.rbac.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 관리자(Admin) 전용 컨트롤러
 *
 * 보안 이중 레이어:
 * 1. SecurityConfig 에서 URL 패턴 "/api/admin/**" → hasRole("ADMIN")
 * 2. @PreAuthorize("hasRole('ADMIN')") 메서드 단위 재검증
 *
 * 이중 레이어를 적용하는 이유:
 * - 추후 URL 패턴 변경 시 메서드 단위 어노테이션이 최후 보루 역할
 * - 코드만 봐도 해당 메서드의 권한 요구사항을 명확히 파악 가능
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /**
     * GET /api/admin/users
     * 전체 사용자 목록 조회 (ADMIN 전용)
     */
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }
}

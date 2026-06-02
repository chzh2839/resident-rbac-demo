package com.example.rbac.controller;

import com.example.rbac.dto.UserResponse;
import com.example.rbac.entity.Role;
import com.example.rbac.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 매니저(Manager) 컨트롤러
 * ADMIN 또는 MANAGER 역할 접근 가능
 *
 * GET /api/manager/residents : 거주자 목록 조회 (관리 목적)
 */
@RestController
@RequestMapping("/api/manager")
@RequiredArgsConstructor
public class ManagerController {

    private final UserRepository userRepository;

    /**
     * GET /api/manager/residents
     * 거주자 목록 조회 (ADMIN 또는 MANAGER 전용)
     */
    @GetMapping("/residents")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<UserResponse>> getResidents() {
        List<UserResponse> residents = userRepository.findAllByRole(Role.RESIDENT)
                .stream()
                .map(UserResponse::from)
                .toList();
        return ResponseEntity.ok(residents);
    }
}

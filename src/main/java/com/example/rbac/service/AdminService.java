package com.example.rbac.service;

import com.example.rbac.dto.AuditLogResponse;
import com.example.rbac.dto.UserResponse;
import com.example.rbac.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 관리자(Admin) 서비스
 * 전체 사용자 목록 조회 등 운영자 전용 기능 제공
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    /** 전체 사용자 목록 조회 */
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    /** 감사 로그 전체 조회 (최신순) */
    public List<AuditLogResponse> getAuditLogs() {
        return auditLogService.getAllLogs();
    }
}

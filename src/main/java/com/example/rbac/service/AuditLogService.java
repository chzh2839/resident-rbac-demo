package com.example.rbac.service;

import com.example.rbac.dto.AuditLogResponse;
import com.example.rbac.entity.AuditAction;
import com.example.rbac.entity.AuditLog;
import com.example.rbac.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 감사 로그 서비스
 *
 * DB 저장과 동시에 log.info/log.warn 으로도 남겨 콘솔/로그 수집기에서 바로 확인 가능하게 한다.
 * ADMIN_API_ACCESS 는 statusCode 로 성공/거부를 구분해 info/warn 레벨을 나눈다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuditLogService {

    private static final int LOGIN_SUCCESS_STATUS = 200;
    private static final int LOGIN_FAILURE_STATUS = 401;
    private static final int LOGOUT_STATUS = 200;

    private final AuditLogRepository auditLogRepository;

    public void recordLoginSuccess(String username, String ipAddress) {
        save(username, AuditAction.LOGIN_SUCCESS, "POST", "/api/auth/login", LOGIN_SUCCESS_STATUS, ipAddress);
        log.info("[AUDIT] LOGIN_SUCCESS username={} ip={}", username, ipAddress);
    }

    public void recordLoginFailure(String attemptedUsername, String ipAddress) {
        save(attemptedUsername, AuditAction.LOGIN_FAILURE, "POST", "/api/auth/login", LOGIN_FAILURE_STATUS, ipAddress);
        log.warn("[AUDIT] LOGIN_FAILURE attemptedUsername={} ip={}", attemptedUsername, ipAddress);
    }

    public void recordLogout(String username, String ipAddress) {
        save(username, AuditAction.LOGOUT, "POST", "/api/auth/logout", LOGOUT_STATUS, ipAddress);
        log.info("[AUDIT] LOGOUT username={} ip={}", username, ipAddress);
    }

    public void recordAdminAccess(String username, String httpMethod, String requestUri, int statusCode, String ipAddress) {
        save(username, AuditAction.ADMIN_API_ACCESS, httpMethod, requestUri, statusCode, ipAddress);
        if (statusCode >= 200 && statusCode < 300) {
            log.info("[AUDIT] ADMIN_API_ACCESS username={} {} {} status={} ip={}",
                    username, httpMethod, requestUri, statusCode, ipAddress);
        } else {
            log.warn("[AUDIT] ADMIN_API_ACCESS(DENIED) username={} {} {} status={} ip={}",
                    username, httpMethod, requestUri, statusCode, ipAddress);
        }
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAllLogs() {
        return auditLogRepository.findAllByOrderByCreatedAtDescIdDesc()
                .stream()
                .map(AuditLogResponse::from)
                .toList();
    }

    private void save(String username, AuditAction action, String httpMethod,
                       String requestUri, int statusCode, String ipAddress) {
        auditLogRepository.save(AuditLog.builder()
                .username(username)
                .action(action)
                .httpMethod(httpMethod)
                .requestUri(requestUri)
                .statusCode(statusCode)
                .ipAddress(ipAddress)
                .build());
    }
}

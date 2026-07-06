package com.example.rbac.dto;

import com.example.rbac.entity.AuditAction;
import com.example.rbac.entity.AuditLog;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/** 감사 로그 응답 DTO */
@Getter
@AllArgsConstructor
public class AuditLogResponse {

    private Long id;
    private String username;
    private AuditAction action;
    private String httpMethod;
    private String requestUri;
    private int statusCode;
    private String ipAddress;
    private LocalDateTime createdAt;

    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getUsername(),
                auditLog.getAction(),
                auditLog.getHttpMethod(),
                auditLog.getRequestUri(),
                auditLog.getStatusCode(),
                auditLog.getIpAddress(),
                auditLog.getCreatedAt()
        );
    }
}

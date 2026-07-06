package com.example.rbac.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 감사 로그 엔티티
 *
 * 인증 성공/실패, ADMIN 전용 API 호출 이력을 DB에 남긴다.
 * LOGIN_FAILURE 의 경우 DB에 존재하지 않는 사용자일 수 있으므로
 * username 은 FK가 아닌 문자열로만 저장한다 (요청에 실린 값 그대로).
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuditAction action;

    @Column(length = 10)
    private String httpMethod;

    @Column(length = 500)
    private String requestUri;

    @Column(nullable = false)
    private int statusCode;

    @Column(length = 45)
    private String ipAddress;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

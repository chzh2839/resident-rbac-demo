package com.example.rbac.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자 엔티티
 * 운영자·매니저·거주자 공통 사용자 테이블.
 * Role 컬럼으로 권한을 구분한다.
 */
@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 200)
    private String email;

    /** 단일 역할 모델 - 역할은 하나만 가진다 (확장 시 UserRole 조인 테이블로 분리 가능) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** 거주자의 경우 동/호수 정보 (ADMIN, MANAGER 는 null) */
    @Column(length = 20)
    private String unitNumber;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

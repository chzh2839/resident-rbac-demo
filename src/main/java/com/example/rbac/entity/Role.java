package com.example.rbac.entity;

/**
 * 역할 열거형
 * - ADMIN  : 운영자 - 전체 시스템 관리, 모든 사용자 조회/수정
 * - MANAGER: 매니저 - 거주자 관리, 공지사항 작성, 부분적 관리 권한
 * - RESIDENT: 거주자 - 본인 프로필 조회, 공지사항 열람
 */
public enum Role {
    ADMIN,
    MANAGER,
    RESIDENT
}

package com.example.rbac.entity;

/**
 * 감사 로그 이벤트 타입
 * - LOGIN_SUCCESS    : 로그인 성공
 * - LOGIN_FAILURE    : 로그인 실패 (잘못된 비밀번호 / 존재하지 않는 사용자)
 * - LOGOUT           : 로그아웃 (Access Token 블랙리스트 등록 + Refresh Token 폐기)
 * - ADMIN_API_ACCESS : /api/admin/** 호출 (성공/거부 모두 포함, statusCode 로 구분)
 */
public enum AuditAction {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT,
    ADMIN_API_ACCESS
}
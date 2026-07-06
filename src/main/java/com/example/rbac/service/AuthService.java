package com.example.rbac.service;

import com.example.rbac.dto.LoginRequest;
import com.example.rbac.dto.LoginResponse;
import com.example.rbac.entity.User;
import com.example.rbac.repository.UserRepository;
import com.example.rbac.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 인증 서비스
 *
 * 고려사항:
 * - AuthenticationManager 위임으로 Spring Security 표준 인증 흐름 유지
 * - 인증 성공 시 DB 에서 role 로드 후 JWT 에 포함 (Claim 최소화)
 * - 인증 성공/실패, 로그아웃을 감사 로그로 기록 (보안 감사 대비)
 * - Access Token(15분) + Refresh Token(7일) 조합으로 재로그인 부담과 탈취 노출 시간을 함께 줄임
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final TokenService tokenService;

    public LoginResponse login(LoginRequest request, String ipAddress) {
        // 1. Spring Security 인증 위임
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            auditLogService.recordLoginFailure(request.getUsername(), ipAddress);
            throw e;
        }

        // 2. 인증된 사용자의 역할 추출
        String role = extractRole(authentication);

        // 3. Access Token + Refresh Token 발급
        String accessToken = jwtTokenProvider.createToken(request.getUsername(), role);
        String refreshToken = tokenService.issueRefreshToken(request.getUsername());

        auditLogService.recordLoginSuccess(request.getUsername(), ipAddress);

        return LoginResponse.of(accessToken, refreshToken, role, request.getUsername());
    }

    /**
     * Refresh Token으로 Access Token 재발급.
     * 회전된 Refresh Token을 함께 반환하고, role은 DB에서 새로 조회해 반영한다
     * (역할 변경이 최대 Access Token 수명(15분) 내에 반영되는 지점).
     */
    public LoginResponse refresh(String rawRefreshToken) {
        TokenService.RotationResult rotation = tokenService.validateAndRotateRefreshToken(rawRefreshToken);

        User user = userRepository.findByUsername(rotation.username())
                .orElseThrow(() -> new BadCredentialsException("사용자를 찾을 수 없습니다."));

        String accessToken = jwtTokenProvider.createToken(user.getUsername(), user.getRole().name());

        return LoginResponse.of(accessToken, rotation.refreshToken(), user.getRole().name(), user.getUsername());
    }

    /** 로그아웃: 현재 Access Token 블랙리스트 등록 + 해당 사용자의 Refresh Token 전량 폐기 */
    public void logout(String rawAccessToken, String ipAddress) {
        String username = jwtTokenProvider.getUsername(rawAccessToken);
        String jti = jwtTokenProvider.getJti(rawAccessToken);
        Instant expiresAt = jwtTokenProvider.getExpiration(rawAccessToken).toInstant();

        tokenService.blacklistAccessToken(jti, expiresAt);
        tokenService.revokeAllRefreshTokens(username);
        auditLogService.recordLogout(username, ipAddress);
    }

    private String extractRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElseThrow()
                .replace("ROLE_", "");
    }
}

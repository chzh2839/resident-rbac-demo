package com.example.rbac.service;

import com.example.rbac.dto.LoginRequest;
import com.example.rbac.dto.LoginResponse;
import com.example.rbac.entity.Role;
import com.example.rbac.entity.User;
import com.example.rbac.repository.UserRepository;
import com.example.rbac.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * AuthService 단위 테스트
 * AuthenticationManager/JwtTokenProvider/UserRepository/AuditLogService/TokenService를
 * 모두 모킹해 로그인/리프레시/로그아웃 흐름을 격리 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private AuthService authService;

    // ============================================================
    // 1. login() 성공
    // ============================================================

    @Test
    @DisplayName("올바른 자격증명으로 로그인 성공 시 access/refresh 토큰과 role을 반환한다")
    void login_returnsTokensOnSuccess() {
        LoginRequest request = loginRequest("admin", "admin123");
        Authentication authentication = mock(Authentication.class);
        doReturn(adminAuthorities()).when(authentication).getAuthorities();
        given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willReturn(authentication);
        given(jwtTokenProvider.createToken("admin", "ADMIN")).willReturn("access-token-xyz");
        given(tokenService.issueRefreshToken("admin")).willReturn("refresh-token-xyz");

        LoginResponse response = authService.login(request, "127.0.0.1");

        assertThat(response.getAccessToken()).isEqualTo("access-token-xyz");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token-xyz");
        assertThat(response.getRole()).isEqualTo("ADMIN");
        assertThat(response.getUsername()).isEqualTo("admin");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        verify(auditLogService).recordLoginSuccess("admin", "127.0.0.1");
        verify(auditLogService, never()).recordLoginFailure(any(), any());
    }

    @Test
    @DisplayName("authenticate() 호출 시 username/password로 UsernamePasswordAuthenticationToken을 생성해 위임한다")
    void login_delegatesCredentialsToAuthenticationManager() {
        LoginRequest request = loginRequest("admin", "admin123");
        Authentication authentication = mock(Authentication.class);
        doReturn(adminAuthorities()).when(authentication).getAuthorities();
        given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willReturn(authentication);

        authService.login(request, "127.0.0.1");

        ArgumentCaptor<UsernamePasswordAuthenticationToken> captor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertThat(captor.getValue().getPrincipal()).isEqualTo("admin");
        assertThat(captor.getValue().getCredentials()).isEqualTo("admin123");
    }

    // ============================================================
    // 2. login() 실패 (BadCredentialsException)
    // ============================================================

    @Test
    @DisplayName("잘못된 자격증명이면 BadCredentialsException을 던지고 로그인 실패를 기록한다")
    void login_throwsAndRecordsFailureOnBadCredentials() {
        LoginRequest request = loginRequest("admin", "wrong-password");
        given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willThrow(new BadCredentialsException("bad creds"));

        assertThatThrownBy(() -> authService.login(request, "127.0.0.1"))
                .isInstanceOf(BadCredentialsException.class);

        verify(auditLogService).recordLoginFailure("admin", "127.0.0.1");
        verifyNoInteractions(jwtTokenProvider, tokenService);
    }

    // ============================================================
    // 3. refresh() 성공/실패
    // ============================================================

    @Test
    @DisplayName("유효한 refresh token으로 재발급 시 최신 DB role을 반영한 새 access token을 반환한다")
    void refresh_returnsNewAccessTokenWithLatestRole() {
        given(tokenService.validateAndRotateRefreshToken("raw-refresh"))
                .willReturn(new TokenService.RotationResult("new-raw-refresh", "admin"));
        User user = User.builder().username("admin").role(Role.ADMIN).build();
        given(userRepository.findByUsername("admin")).willReturn(Optional.of(user));
        given(jwtTokenProvider.createToken("admin", "ADMIN")).willReturn("new-access-token");

        LoginResponse response = authService.refresh("raw-refresh");

        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-raw-refresh");
        assertThat(response.getRole()).isEqualTo("ADMIN");
        assertThat(response.getUsername()).isEqualTo("admin");
    }

    @Test
    @DisplayName("회전 후 조회된 사용자가 DB에 없으면 BadCredentialsException 발생")
    void refresh_throwsWhenUserNotFound() {
        given(tokenService.validateAndRotateRefreshToken("raw-refresh"))
                .willReturn(new TokenService.RotationResult("new-raw-refresh", "ghost"));
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("raw-refresh"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("사용자를 찾을 수 없습니다.");
    }

    // ============================================================
    // 4. logout()
    // ============================================================

    @Test
    @DisplayName("로그아웃 시 access token을 블랙리스트에 등록하고 refresh token을 전량 폐기하며 감사 로그를 남긴다")
    void logout_blacklistsRevokesAndRecordsAudit() {
        given(jwtTokenProvider.getUsername("raw-access")).willReturn("admin");
        given(jwtTokenProvider.getJti("raw-access")).willReturn("jti-123");
        given(jwtTokenProvider.getExpiration("raw-access"))
                .willReturn(Date.from(Instant.now().plusSeconds(600)));

        authService.logout("raw-access", "127.0.0.1");

        verify(tokenService).blacklistAccessToken(eq("jti-123"), any(Instant.class));
        verify(tokenService).revokeAllRefreshTokens("admin");
        verify(auditLogService).recordLogout("admin", "127.0.0.1");
    }

    private List<GrantedAuthority> adminAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        ReflectionTestUtils.setField(request, "username", username);
        ReflectionTestUtils.setField(request, "password", password);
        return request;
    }
}

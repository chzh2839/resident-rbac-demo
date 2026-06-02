package com.example.rbac.service;

import com.example.rbac.dto.LoginRequest;
import com.example.rbac.dto.LoginResponse;
import com.example.rbac.entity.User;
import com.example.rbac.repository.UserRepository;
import com.example.rbac.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

/**
 * 인증 서비스
 *
 * 고려사항:
 * - AuthenticationManager 위임으로 Spring Security 표준 인증 흐름 유지
 * - 인증 성공 시 DB 에서 role 로드 후 JWT 에 포함 (Claim 최소화)
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public LoginResponse login(LoginRequest request) {
        // 1. Spring Security 인증 위임
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        // 2. 인증된 사용자의 역할 추출
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElseThrow()
                .replace("ROLE_", "");

        // 3. JWT 발급
        String token = jwtTokenProvider.createToken(request.getUsername(), role);

        return LoginResponse.of(token, role, request.getUsername());
    }
}

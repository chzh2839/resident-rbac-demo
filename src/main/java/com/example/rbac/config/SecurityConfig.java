package com.example.rbac.config;

import com.example.rbac.security.AuditLogFilter;
import com.example.rbac.security.JwtAuthenticationFilter;
import com.example.rbac.security.JwtTokenProvider;
import com.example.rbac.service.AuditLogService;
import com.example.rbac.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 설정
 *
 * 고려사항:
 * 1. Stateless 세션 정책 → JWT 기반 인증으로 서버 세션 불필요
 * 2. @EnableMethodSecurity → 컨트롤러 메서드 단위 @PreAuthorize 활성화
 * 3. URL 패턴 기반 1차 접근 제어 + 메서드 단위 2차 접근 제어 이중 레이어
 * 4. BCrypt 패스워드 인코더 (strength 기본값 10)
 * 5. H2 콘솔 접근 허용 (개발 환경용)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity           // @PreAuthorize, @PostAuthorize 활성화
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuditLogService auditLogService;
    private final TokenService tokenService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // REST API → CSRF 불필요
            .csrf(AbstractHttpConfigurer::disable)

            // H2 console iframe 허용 (개발용)
            .headers(headers -> headers.frameOptions(fo -> fo.sameOrigin()))

            // Stateless: JWT 사용으로 서버 세션 미사용
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // URL 패턴 기반 접근 제어
            .authorizeHttpRequests(auth -> auth
                // 공개 엔드포인트 (로그인/리프레시는 JWT 인증 이전 단계라 permitAll)
                .requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
                // 로그아웃은 현재 Access Token을 검증해야 하므로 인증 필요
                .requestMatchers("/api/auth/logout").authenticated()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()

                // 관리자 전용 URL 레벨 보호 (메서드 레벨과 이중 보호)
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // 매니저 이상 접근 가능
                .requestMatchers("/api/manager/**").hasAnyRole("ADMIN", "MANAGER")

                // 인증된 사용자라면 모두 접근 가능한 경로
                .requestMatchers("/api/resident/**").authenticated()

                // 나머지는 인증 필요
                .anyRequest().authenticated()
            )

            // JWT 필터를 UsernamePasswordAuthenticationFilter 앞에 배치
            .addFilterBefore(
                new JwtAuthenticationFilter(jwtTokenProvider, tokenService),
                UsernamePasswordAuthenticationFilter.class
            )

            // 감사 로그 필터: JWT 필터 직후, FilterSecurityInterceptor 이전에 배치해
            // /api/admin/** 호출의 성공(2xx)/거부(403)를 모두 기록한다
            .addFilterAfter(
                new AuditLogFilter(auditLogService),
                JwtAuthenticationFilter.class
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}

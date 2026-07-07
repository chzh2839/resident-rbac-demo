package com.example.rbac.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JwtTokenProvider 단위 테스트
 * 협력 객체가 없는 순수 컴포넌트라 Spring 컨텍스트/Mockito 없이 직접 생성해 검증한다.
 */
class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-at-least-32-bytes-long-1234";
    private static final String OTHER_SECRET = "different-secret-key-that-is-also-32b-plus";
    private static final long VALIDITY_MS = 3600_000L; // 1시간

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(SECRET, VALIDITY_MS);
    }

    // ============================================================
    // 1. 토큰 발급 (createToken)
    // ============================================================

    @Test
    @DisplayName("토큰 발급 시 subject, role, jti, 만료시간이 올바르게 설정된다")
    void createToken_setsClaimsCorrectly() {
        String token = tokenProvider.createToken("admin", "ADMIN");

        assertThat(tokenProvider.getUsername(token)).isEqualTo("admin");
        assertThat(tokenProvider.getRole(token)).isEqualTo("ADMIN");
        assertThat(tokenProvider.getJti(token)).isNotBlank();
        assertThat(tokenProvider.getExpiration(token)).isAfter(new Date());
    }

    @Test
    @DisplayName("토큰 발급마다 서로 다른 jti(고유 ID)가 생성된다")
    void createToken_generatesUniqueJti() {
        String token1 = tokenProvider.createToken("admin", "ADMIN");
        String token2 = tokenProvider.createToken("admin", "ADMIN");

        assertThat(tokenProvider.getJti(token1)).isNotEqualTo(tokenProvider.getJti(token2));
    }

    @Test
    @DisplayName("발급된 토큰은 validateToken()에서 유효함으로 판단된다")
    void createToken_isValid() {
        String token = tokenProvider.createToken("resident1", "RESIDENT");

        assertThat(tokenProvider.validateToken(token)).isTrue();
    }

    // ============================================================
    // 2. Claim 추출
    // ============================================================

    @Test
    @DisplayName("role이 다른 토큰들은 각각 올바른 role을 반환한다")
    void getRole_returnsCorrectRolePerToken() {
        String adminToken = tokenProvider.createToken("admin", "ADMIN");
        String managerToken = tokenProvider.createToken("manager1", "MANAGER");

        assertThat(tokenProvider.getRole(adminToken)).isEqualTo("ADMIN");
        assertThat(tokenProvider.getRole(managerToken)).isEqualTo("MANAGER");
    }

    // ============================================================
    // 3. 유효성 검증 실패 케이스 (validateToken)
    // ============================================================

    @Test
    @DisplayName("만료된 토큰은 validateToken()이 false를 반환한다")
    void validateToken_returnsFalseForExpiredToken() {
        String expired = buildToken(SECRET, "admin", "ADMIN",
                Instant.now().minusSeconds(120), Instant.now().minusSeconds(60));

        assertThat(tokenProvider.validateToken(expired)).isFalse();
    }

    @Test
    @DisplayName("서명 키가 다른 토큰은 validateToken()이 false를 반환한다")
    void validateToken_returnsFalseForWrongSignature() {
        String foreignlySigned = buildToken(OTHER_SECRET, "admin", "ADMIN",
                Instant.now(), Instant.now().plusSeconds(3600));

        assertThat(tokenProvider.validateToken(foreignlySigned)).isFalse();
    }

    @Test
    @DisplayName("형식이 깨진(malformed) 토큰은 validateToken()이 false를 반환한다")
    void validateToken_returnsFalseForMalformedToken() {
        assertThat(tokenProvider.validateToken("not-a-jwt-at-all")).isFalse();
        assertThat(tokenProvider.validateToken("")).isFalse();
        assertThat(tokenProvider.validateToken("a.b.c")).isFalse();
    }

    @Test
    @DisplayName("null 토큰은 validateToken()이 false를 반환한다")
    void validateToken_returnsFalseForNullToken() {
        assertThat(tokenProvider.validateToken(null)).isFalse();
    }

    private String buildToken(String secret, String username, String role, Instant issuedAt, Instant expiration) {
        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setSubject(username)
                .claim("role", role)
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(expiration))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}

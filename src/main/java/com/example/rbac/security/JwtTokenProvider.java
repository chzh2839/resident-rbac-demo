package com.example.rbac.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 토큰 생성 / 검증 유틸리티
 *
 * 고려사항:
 * - 서명 알고리즘: HS256 (HMAC-SHA256) 사용
 * - 토큰 만료: 기본 24시간 (설정으로 변경 가능)
 * - Claim 에 role 포함 → SecurityContext 설정 시 DB 조회 없이 역할 판단
 */
@Component
public class JwtTokenProvider {

    private final Key key;
    private final long validityMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.validity-ms:86400000}") long validityMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.validityMs = validityMs;
    }

    /** 토큰 발급 */
    public String createToken(String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setSubject(username)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + validityMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** 토큰에서 username 추출 */
    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /** 토큰에서 role 추출 */
    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /** 토큰에서 jti(고유 ID) 추출 */
    public String getJti(String token) {
        return parseClaims(token).getId();
    }

    /** 토큰 만료 시각 추출 */
    public Date getExpiration(String token) {
        return parseClaims(token).getExpiration();
    }

    /** 토큰 유효성 검증 */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}

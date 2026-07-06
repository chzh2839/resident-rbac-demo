# Spring Boot RBAC 데모 — 역할 기반 접근 제어

---

## 목차

1. [프로젝트 소개](#1-프로젝트-소개)
2. [해결하려 한 문제](#2-해결하려-한-문제)
3. [기술 스택](#3-기술-스택)
4. [아키텍처 개요](#4-아키텍처-개요)
5. [권한 모델](#5-권한-모델)
6. [ERD](#6-erd)
7. [API 구조](#7-api-구조)
8. [기술적 선택 근거](#8-기술적-선택-근거)
9. [트러블슈팅](#9-트러블슈팅)
10. [한계 및 개선 방향](#10-한계-및-개선-방향)
11. [테스트](#11-테스트)
12. [실행 방법](#12-실행-방법)

---

## 1. 프로젝트 소개

Spring Security에서 JWT 기반 인증을 RBAC(Role-Based Access Control)와 함께 구현하는 방법을 직접 손으로 짜보고 싶어서 만들었다. 라이브러리가 알아서 해주는 부분이 많다 보니 "어느 지점에서 무엇이 일어나는가"를 정확히 모른 채로 쓰는 게 불편했고, 필터 체인부터 메서드 보안까지 직접 제어하면서 흐름을 이해하는 것이 목표였다.

도메인은 공동주택 관리 시스템으로 잡았다. 동일한 시스템을 사용하지만 운영자(ADMIN), 매니저(MANAGER), 거주자(RESIDENT)가 접근할 수 있는 데이터와 기능이 완전히 달라야 하는 상황이 RBAC를 설명하기에 딱 맞았다.

---

## 2. 해결하려 한 문제

| 과제 | 구현 방식 |
|------|-----------|
| 거주자가 다른 거주자의 개인정보를 조회할 수 있는 구조 | URL에 식별자 노출 없이 SecurityContext의 username으로만 프로필 조회 (IDOR 방어) |
| 역할 구분 없이 로그인 여부만 확인하는 단순 접근 제어 | URL 패턴 보호(1차) + 메서드 단위 `@PreAuthorize`(2차) 이중 레이어 |
| 매 요청마다 DB에서 권한을 조회하는 구조 | JWT 클레임에 role을 포함해 토큰만으로 권한 판단 |
| 토큰 기반 인증인데 서버 세션이 남아 있는 구조 | Stateless 정책 명시, SecurityContext를 요청 단위로만 유지 |

---

## 3. 기술 스택

```
Language   Java 17
Framework  Spring Boot 3.2
Security   Spring Security 6 + jjwt 0.11.5 (HS256)
DB         H2 In-Memory (개발용)
ORM        Spring Data JPA / Hibernate
Build      Maven
Test       JUnit 5, Spring MockMvc
```

---

## 4. 아키텍처 개요

```
HTTP 요청
    │
    ▼
┌──────────────────────────────┐
│   JwtAuthenticationFilter    │  Authorization 헤더에서 Bearer 토큰 추출
│                              │  → 서명 검증 → username, role 클레임 추출
│                              │  → SecurityContext에 Authentication 설정
└─────────────┬────────────────┘
              │  (토큰 없거나 유효하지 않으면 anonymous로 통과)
              ▼
┌──────────────────────────────┐
│  SecurityConfig              │  URL 패턴 기반 1차 접근 제어
│  authorizeHttpRequests()     │  /api/admin/**   → ADMIN만
│                              │  /api/manager/** → ADMIN or MANAGER
│                              │  /api/resident/**→ 인증된 사용자
└─────────────┬────────────────┘
              │
              ▼
┌──────────────────────────────┐
│  @PreAuthorize (Controller)  │  메서드 단위 2차 접근 제어
│                              │  1차와 동일한 조건 재검증 → 최후 보루
└─────────────┬────────────────┘
              │
              ▼
         비즈니스 로직
```

필터에서 SecurityContext를 채우면 이후 필터 체인 전체에서 인증 정보를 공유한다. 세션에 저장하지 않으므로 요청이 끝나면 컨텍스트가 비워진다.

---

## 5. 권한 모델

```
ADMIN
 ├── /api/admin/**       ← 전용 (전체 사용자 조회, 시스템 관리)
 └── /api/manager/**     ← 접근 가능 (거주자 목록 조회)

MANAGER
 └── /api/manager/**     ← 전용 (거주자 목록 조회)

RESIDENT
 └── /api/resident/**    ← 전용 (본인 프로필만)
```

| Role | 설명 | 접근 가능 API |
|------|------|--------------|
| `ADMIN` | 운영자. 전체 시스템 관리 권한 | `/api/admin/**`, `/api/manager/**` |
| `MANAGER` | 매니저. 거주자 관리 권한 | `/api/manager/**` |
| `RESIDENT` | 거주자. 본인 데이터만 접근 | `/api/resident/**` (본인 한정) |

---

## 6. ERD

```
┌─────────────────────────────────────────┐
│                  users                  │
├──────────────┬──────────────────────────┤
│ id           │ BIGINT (PK, AUTO)        │
│ username     │ VARCHAR(50) UNIQUE       │
│ password     │ VARCHAR (BCrypt)         │
│ name         │ VARCHAR(100)             │
│ email        │ VARCHAR(200)             │
│ role         │ ENUM(ADMIN,MANAGER,      │
│              │      RESIDENT)           │
│ unit_number  │ VARCHAR(20) NULLABLE     │  ← 거주자만 사용 (동/호수)
│ created_at   │ DATETIME                 │
└──────────────┴──────────────────────────┘
```

**설계 결정 — 단일 테이블 vs 역할별 테이블**

데모 수준에서는 단일 `users` 테이블에 `role` ENUM 컬럼 하나로 역할을 구분했다. `unit_number`처럼 거주자에게만 의미 있는 컬럼이 섞여 있지만, 테이블을 여러 개로 쪼개면 JOIN이 늘어나고 코드가 복잡해지는 반면 데모에서 얻는 이득이 크지 않다고 판단했다.

실제 서비스라면 역할마다 고유 속성이 충분히 많을 때 `resident_profile` 같은 1:1 상세 테이블로 분리하거나, 역할이 자주 추가·변경된다면 `users + user_roles` 조인 테이블 구조를 고려할 것이다.

```
┌─────────────────────────────────────────┐
│               audit_logs                │
├──────────────┬──────────────────────────┤
│ id           │ BIGINT (PK, AUTO)        │
│ username     │ VARCHAR(50)              │  ← FK 아님, 문자열 그대로 저장
│ action       │ ENUM(LOGIN_SUCCESS,      │
│              │      LOGIN_FAILURE,      │
│              │      ADMIN_API_ACCESS)   │
│ http_method  │ VARCHAR(10)              │
│ request_uri  │ VARCHAR(500)             │
│ status_code  │ INT                      │
│ ip_address   │ VARCHAR(45)              │
│ created_at   │ DATETIME                 │
└──────────────┴──────────────────────────┘
```

`username`을 `users.id`에 대한 FK로 두지 않은 이유가 있다. `LOGIN_FAILURE`는 오타나 존재하지 않는 계정으로 로그인을 시도한 경우에도 남겨야 하는데, 이런 시도는 애초에 `users` 테이블에 대응하는 행이 없다.
감사 로그의 목적이 "시도 자체"를 기록하는 것이므로 FK 제약 없이 문자열로만 저장했다.

---

## 7. API 구조

### 인증

| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| `POST` | `/api/auth/login` | 없음 | 로그인 → JWT 발급 |

```
요청  { "username": "admin", "password": "admin123" }
응답  { "accessToken": "eyJhbGci...", "tokenType": "Bearer", "role": "ADMIN", "username": "admin" }
```

---

### 관리자 (ADMIN 전용)

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/api/admin/users` | 전체 사용자 목록 조회 |
| `GET` | `/api/admin/audit-logs` | 감사 로그 조회 (최신순) |

---

### 매니저 (ADMIN, MANAGER 접근)

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/api/manager/residents` | 거주자 목록 조회 |

---

### 거주자 (RESIDENT 전용)

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/api/resident/profile` | 본인 프로필 조회 |

```
응답  { "id": 3, "name": "이거주", "email": "resident1@email.com", "unitNumber": "101호" }
```

---

### 개발용

| Path | 설명 |
|------|------|
| `GET /h2-console` | 인메모리 DB 조회 (JDBC URL: `jdbc:h2:mem:rbacdb`) |
| `GET /actuator/health` | 서버 상태 확인 |

---

## 8. 기술적 선택 근거

### 8-1. 이중 접근 제어 레이어 (URL 패턴 보호 + @PreAuthorize)

URL 패턴 보호만 있으면 SecurityConfig를 고치거나 URL 구조가 바뀌는 순간 보호가 무너진다. 반대로 메서드 어노테이션만 있으면 URL 수준에서 에러 응답이 늦게 나오고, 어노테이션이 빠진 메서드가 의도치 않게 열릴 수 있다.

두 레이어를 함께 쓰면 1차(SecurityConfig)가 대부분의 요청을 걸러내고, 2차(`@PreAuthorize`)가 코드 레벨에서 최후 보루 역할을 한다. 코드 리뷰 시 컨트롤러 메서드만 봐도 어떤 역할이 필요한지 즉시 확인할 수도 있다.

### 8-2. JWT 클레임에 role 포함 → 요청당 DB 조회 없음

토큰 안에 `role` 클레임을 포함시켜 필터에서 바로 꺼내 쓴다. 매 요청마다 DB에서 사용자 역할을 조회하지 않아도 된다. stateless 구조이므로 인스턴스를 수평으로 늘려도 세션 공유 문제가 없다.

절충점은 있다. DB에서 특정 사용자의 역할을 바꿔도 기존 토큰이 만료되기 전까지는 이전 역할로 계속 동작한다. 보완 방법과 각 방식의 트레이드오프는 [10. 한계 및 개선 방향](#10-한계-및-개선-방향)에서 다룬다.

### 8-3. principal을 UserDetails가 아닌 String으로 저장

```java
// JwtAuthenticationFilter.java
UsernamePasswordAuthenticationToken authentication =
    new UsernamePasswordAuthenticationToken(username, null, authorities);
```

폼 로그인 구현에서는 `CustomUserDetailsService`가 DB에서 사용자를 조회해 `UserDetails` 객체를 principal로 넣는다. 하지만 이 프로젝트는 JWT 검증 후 토큰 클레임만으로 인증을 완성하는 구조라 DB 조회가 불필요하다. username 문자열을 principal로 바로 넣고, 컨트롤러에서도 `@AuthenticationPrincipal String username`으로 받으면 타입이 일치한다.

`@AuthenticationPrincipal UserDetails userDetails`로 받으면 `null`이 주입된다. Spring Security의 `AuthenticationPrincipalArgumentResolver`가 타입 불일치 시 예외 대신 `null`을 반환하기 때문이다. 실제로 이 문제를 겪었고, 트러블슈팅 9-1에 기록했다.

### 8-4. IDOR 방어 — URL에 식별자 노출하지 않기

`/api/resident/profile/{id}` 처럼 ID를 URL에 받으면 `resident1`이 값을 바꿔 다른 거주자 정보를 볼 수 있다. 이런 IDOR(Insecure Direct Object Reference) 취약점을 막으려면 식별자를 요청 파라미터가 아닌 SecurityContext에서 가져와야 한다.

```java
// ResidentController.java
public ResponseEntity<ResidentProfileResponse> getMyProfile(
        @AuthenticationPrincipal String username) {
    return ResponseEntity.ok(residentService.getMyProfile(username));
}
```

토큰이 유효한 이상 username은 조작할 수 없으므로 항상 본인 데이터만 반환한다.

### 8-5. ROLE_ 접두사 규칙

Spring Security의 `hasRole("ADMIN")`은 내부적으로 `"ROLE_ADMIN"` 권한을 찾는다. JWT 클레임에는 `"ADMIN"`만 담고, 필터에서 SecurityContext에 등록할 때 `"ROLE_"` 접두사를 붙인다. `hasAuthority("ADMIN")`을 쓰면 접두사 없이도 매칭되지만, `hasRole` 표기를 일관되게 유지하는 쪽이 Spring Security 규칙에 맞는다고 판단했다.

### 8-6. 감사 로그를 Filter로 구현한 이유

`/api/admin/**`에 대한 인가 거부(403)는 `ExceptionTranslationFilter`가 필터 체인 내부에서 완결시키므로 컨트롤러나 `@RestControllerAdvice`(`GlobalExceptionHandler`)에는 도달하지 않는다.
`@RestControllerAdvice` 방식만으로는 "권한 없는 사용자가 관리자 API를 두드렸다"는, 감사 로그에서 가장 중요한 이벤트를 놓치게 된다.

그래서 `AuditLogFilter`를 `JwtAuthenticationFilter` 바로 다음, `FilterSecurityInterceptor` 이전 위치에 등록했다.
`filterChain.doFilter()` 호출을 감싸고, 반환된 이후 `response.getStatus()`를 읽으면 200과 403 모두 정확히 관측된다.
로그인 성공/실패는 `AuthService`에서 직접 `AuditLogService`를 호출하는 방식으로 남긴다 — 인증 자체는 필터 체인 바깥의 서비스 로직이라 별도 처리가 자연스럽다.

절충점은 있다. 감사 로그 저장은 요청 처리 경로 안에서 동기적으로 실행되는 JPA save다. 트래픽이 많아지면 관리자 API 응답 지연에 DB 쓰기 지연이 그대로 더해진다.
실서비스라면 큐(Kafka 등)나 비동기 이벤트 발행 후 별도 컨슈머가 저장하는 구조로 분리해 요청 경로에서 감사 로그 저장을 제거하는 편이 낫다.
또한 클라이언트 IP는 `request.getRemoteAddr()`로 기록하는데, 리버스 프록시 뒤에서는 프록시 IP만 잡힌다는 한계도 있다 — 실서비스라면 `X-Forwarded-For` 등을 신뢰할 수 있는 범위 내에서 파싱해야 한다.

---

## 9. 트러블슈팅

### 9-1. @AuthenticationPrincipal 타입 불일치 → NullPointerException

**증상**

`residentCanAccessOwnProfile` 테스트가 200이 아닌 500과 함께 스택 트레이스를 뱉었다.

```
NullPointerException: Cannot invoke "UserDetails.getUsername()" because "userDetails" is null
    at ResidentController.getMyProfile(ResidentController.java:39)
```

**원인**

`JwtAuthenticationFilter`에서 principal을 `String`(username)으로 저장했는데, 컨트롤러는 `@AuthenticationPrincipal UserDetails userDetails`로 받으려 했다. `AuthenticationPrincipalArgumentResolver`는 타입이 맞지 않으면 예외 대신 `null`을 반환한다. 그래서 `userDetails`가 `null`인 채로 들어와 NPE가 발생했다.

처음에는 토큰 자체의 문제인 줄 알고 `JwtTokenProvider`부터 뒤졌는데, 로그인 응답에서 토큰은 정상적으로 발급되고 있었다. 필터에서 SecurityContext에 뭘 넣고 있는지를 확인하고 나서야 타입 문제라는 걸 알았다.

**해결**

필터에서 저장하는 타입과 컨트롤러에서 받는 타입을 일치시켰다.

```java
// 변경 전
public ResponseEntity<ResidentProfileResponse> getMyProfile(
        @AuthenticationPrincipal UserDetails userDetails) {
    return ResponseEntity.ok(residentService.getMyProfile(userDetails.getUsername()));
}

// 변경 후
public ResponseEntity<ResidentProfileResponse> getMyProfile(
        @AuthenticationPrincipal String username) {
    return ResponseEntity.ok(residentService.getMyProfile(username));
}
```

### 9-2. ROLE_ 접두사 누락 → 유효한 토큰인데 403

**증상**

토큰을 발급받아 Authorization 헤더에 실어 보내는데 `/api/admin/users`에서 403이 계속 반환됐다. 필터 로그를 찍어보면 SecurityContext에는 Authentication이 설정되어 있었다.

**원인**

`SimpleGrantedAuthority("ADMIN")`으로 권한을 등록한 것이 문제였다. `hasRole("ADMIN")`은 내부적으로 `"ROLE_ADMIN"`을 찾는데 저장된 값은 `"ADMIN"`이라 매칭이 되지 않았다. 인증은 됐지만 인가에서 걸린 것이다.

**해결**

```java
// 변경 전
new SimpleGrantedAuthority(role)

// 변경 후
new SimpleGrantedAuthority("ROLE_" + role)
```

---

## 10. 한계 및 개선 방향

RBAC의 핵심 흐름을 보여주는 데 집중한 데모라 실서비스에 바로 쓰기에는 빠진 부분이 있다.

- **Refresh Token 없음**

현재 Access Token 하나만 발급한다. 만료(24시간) 시 재로그인이 필요하고, 만료 시간을 짧게 줄이면 사용성이 떨어진다. 실서비스라면 짧은 Access Token(15분)과 긴 Refresh Token을 조합해 만료 시 자동 재발급하는 구조가 필요하다.

- **토큰 무효화 불가**

로그아웃해도 토큰은 서버 측에서 취소할 방법이 없다. 탈취된 토큰도 마찬가지다. Redis 블랙리스트에 무효화된 토큰을 저장하는 방식으로 보완할 수 있다.

- **역할 변경 즉시 반영 안 됨**

DB에서 특정 사용자의 role을 바꿔도 그 사람의 기존 토큰이 만료되기 전까지 이전 역할로 동작한다. JWT 클레임 기반 권한 판단의 구조적 절충이다. 반영 속도를 높이고 싶을 때 선택지는 네 가지다.

1. **매 요청마다 DB 조회** — 변경이 즉시 반영된다. 대신 JWT를 쓰는 이유인 stateless 장점이 사라지고 모든 요청이 DB를 탄다.

2. **짧은 Access Token + Refresh Token** — 가장 흔한 절충안이다. Access Token 만료를 15분으로 줄이면 역할이 바뀌어도 최대 15분 내에는 반영된다. Refresh Token으로 재발급할 때만 DB에서 최신 role을 읽으면 되니 DB 조회 빈도는 낮게 유지할 수 있다.

3. **Redis 블랙리스트** — 역할이 바뀐 사용자의 기존 토큰을 Redis에 올린다. 필터에서 요청마다 Redis를 조회해 블랙리스트에 있으면 거부한다. DB보다 훨씬 빠르지만(1ms 수준), 순수 JWT와 달리 요청마다 외부 조회가 한 번 생긴다는 구조적 사실은 동일하다. 비교 기준은 DB vs Redis가 아니라 "외부 조회 0번인 순수 JWT" vs "외부 조회 1번이 생기는 블랙리스트"다.

4. **토큰 버전(jti) 관리** — `users` 테이블에 `token_version` 컬럼을 두고 역할 변경 시 버전을 올린다. 토큰에 발급 시점의 버전을 담아두고, 요청마다 Redis(또는 DB)의 현재 버전과 비교한다. 블랙리스트보다 저장 공간이 훨씬 적게 들지만, 순수 JWT와 달리 요청당 외부 조회가 1번 발생한다는 구조는 동일하다.

결국 외부 조회를 완전히 없애면서 역할 변경을 즉시 반영하는 건 구조적으로 불가능하다. stateless JWT의 본질이 "토큰 자체가 진실의 원천"이기 때문이다. 허용 가능한 지연 시간과 외부 조회 비용 사이에서 선택하는 문제다.

- **단위 테스트 부재**

현재 테스트는 전부 `@SpringBootTest` + MockMvc 통합 테스트다. `JwtTokenProvider`, `ResidentService` 같은 단위를 격리해 테스트하면 어느 레이어에서 문제가 생겼는지 더 빠르게 파악할 수 있다.

- **감사 로그 저장이 동기 I/O**

인증 성공/실패, ADMIN API 호출 이력은 DB에 남긴다(`AuditLog` 엔티티, `/api/admin/audit-logs`에서 조회 가능).
다만 저장은 요청 처리 경로 안의 동기 JPA save로 구현했다 — 데모 범위에서는 단순함이 우선이었다.
실서비스라면 감사 로그 저장 실패나 지연이 원 요청에 영향을 주지 않도록 비동기/큐 기반 저장으로 분리하는 편이 낫다. 설계 근거는 [8-6](#8-6-감사-로그를-filter로-구현한-이유) 참고.

---

## 11. 테스트

`RbacIntegrationTest` — 17개 케이스, `@SpringBootTest` + MockMvc

- ADMIN/MANAGER/RESIDENT 로그인 → JWT 발급 확인
- 잘못된 비밀번호 로그인 → 401
- ADMIN 토큰으로 `/api/admin/users` 조회 → 200
- RESIDENT/MANAGER 토큰으로 `/api/admin/users` 접근 → 403
- RESIDENT 토큰으로 `/api/resident/profile` 조회 → 200, `unitNumber` 값 검증
- ADMIN 토큰으로 `/api/resident/profile` 접근 → 403 (역할 분리 확인)
- MANAGER/ADMIN 토큰으로 `/api/manager/residents` 조회 → 200
- RESIDENT 토큰으로 `/api/manager/residents` 접근 → 403
- 토큰 없이 보호된 엔드포인트 접근 → 403
- 로그인 성공/실패 시 각각 LOGIN_SUCCESS/LOGIN_FAILURE 감사 로그 기록 확인
- ADMIN 토큰으로 `/api/admin/users` 성공(200)/RESIDENT 토큰으로 거부(403) 시 ADMIN_API_ACCESS 감사 로그가 각각 정확한 status로 기록되는지 확인
- ADMIN 토큰으로 `/api/admin/audit-logs` 조회 → 200, MANAGER 토큰 → 403

---

## 12. 실행 방법

Java 17+, Maven 필요.

```powershell
# Windows
.\mvnw spring-boot:run

# 테스트
.\mvnw test
```

```bash
# Mac / Linux
./mvnw spring-boot:run
./mvnw test
```

앱 시작 후 콘솔에서 테스트 계정을 확인할 수 있다.

```
[DataInitializer] ADMIN   : admin / admin123
[DataInitializer] MANAGER : manager1 / manager123
[DataInitializer] RESIDENT: resident1 / resident123  (101호)
[DataInitializer] RESIDENT: resident2 / resident123  (202호)
```

H2 콘솔: http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:rbacdb`)

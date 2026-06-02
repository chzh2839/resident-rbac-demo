# resident-rbac-demo

Spring Boot + Spring Security + JWT 기반의 RBAC(Role-Based Access Control) 데모 프로젝트입니다.  
공동주택(아파트/오피스텔) 관리 시스템을 예시로 역할별 접근 제어를 최소한의 코드로 구현합니다.

---

## 목차

- [프로젝트 목적](#프로젝트-목적)
- [권한 모델](#권한-모델)
- [ERD](#erd)
- [API 구조](#api-구조)
- [구현하면서 고려한 점](#구현하면서-고려한-점)
- [실행 방법](#실행-방법)
- [테스트 시나리오](#테스트-시나리오)

---

## 프로젝트 목적

공동주택 관리 시스템은 **운영자(관리소장), 매니저(담당자), 거주자**가 동일 시스템을 사용하지만  
각자 접근할 수 있는 데이터와 기능의 범위가 달라야 합니다.

| 문제 | 해결 방향 |
|------|-----------|
| 거주자가 타 거주자 개인정보 조회 가능 | RESIDENT는 본인 프로필만 접근 가능하도록 분리 |
| 매니저가 시스템 전체 설정을 변경 가능 | 관리 기능은 ADMIN 전용 엔드포인트로 격리 |
| 역할 없이 단순 로그인/비로그인만 구분 | 3단계 역할 계층 + URL·메서드 이중 보호 |

---

## 권한 모델

```
ADMIN
 ├── /api/admin/**       ← 전용 (사용자 전체 조회, 시스템 관리)
 └── /api/manager/**     ← 접근 가능 (거주자 목록 관리)

MANAGER
 └── /api/manager/**     ← 전용 (거주자 목록 조회, 공지 관리)

RESIDENT
 └── /api/resident/**    ← 전용 (본인 프로필만 접근)
```

### 역할 정의

| Role | 설명 | 접근 가능 API |
|------|------|--------------|
| `ADMIN` | 운영자. 전체 시스템 관리 권한 | `/api/admin/**`, `/api/manager/**` |
| `MANAGER` | 매니저. 거주자 관리 권한 | `/api/manager/**` |
| `RESIDENT` | 거주자. 본인 데이터만 접근 | `/api/resident/**` (본인 한정) |

---

## ERD

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

> **설계 결정 — 단일 테이블 vs 역할별 테이블**  
> 이 데모에서는 단순성을 위해 단일 `users` 테이블에 `role` 컬럼으로 역할을 구분합니다.  
> 실제 서비스에서 역할마다 속성이 크게 다르다면 `users + user_roles` 조인 테이블 또는 역할별 상세 테이블(resident_detail 등)로 분리를 고려할 수 있습니다.

---

## API 구조

### 인증

| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| `POST` | `/api/auth/login` | 없음 | 로그인 → JWT 발급 |

**요청**
```json
{
  "username": "admin",
  "password": "admin123"
}
```

**응답**
```json
{
  "accessToken": "eyJhbGci...",
  "tokenType": "Bearer",
  "role": "ADMIN",
  "username": "admin"
}
```

---

### 관리자 (ADMIN 전용)

| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| `GET` | `/api/admin/users` | `ADMIN` | 전체 사용자 목록 조회 |

**응답**
```json
[
  { "id": 1, "username": "admin", "name": "시스템 관리자", "role": "ADMIN", "unitNumber": null },
  { "id": 3, "username": "resident1", "name": "이거주", "role": "RESIDENT", "unitNumber": "101호" }
]
```

---

### 매니저 (ADMIN + MANAGER 접근)

| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| `GET` | `/api/manager/residents` | `ADMIN` or `MANAGER` | 거주자 목록 조회 |

---

### 거주자 (RESIDENT 전용)

| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| `GET` | `/api/resident/profile` | `RESIDENT` | 본인 프로필 조회 |

**응답**
```json
{
  "id": 3,
  "name": "이거주",
  "email": "resident1@email.com",
  "unitNumber": "101호"
}
```

---

### H2 콘솔 (개발용)

| Path | 설명 |
|------|------|
| `GET /h2-console` | 인메모리 DB 조회 (JDBC URL: `jdbc:h2:mem:rbacdb`) |

---

## 구현하면서 고려한 점

### 1. 이중 접근 제어 레이어

보안을 단일 지점에 의존하지 않고 두 레이어로 중첩 적용했습니다.

```
요청
 │
 ▼
[SecurityConfig] URL 패턴 기반 1차 제어
  .requestMatchers("/api/admin/**").hasRole("ADMIN")
 │
 ▼
[Controller] @PreAuthorize 메서드 단위 2차 제어
  @PreAuthorize("hasRole('ADMIN')")
```

URL 패턴 규칙이 리팩토링 중 변경되더라도 메서드 어노테이션이 최후 보루가 됩니다.  
코드 리뷰 시에도 메서드만 보면 권한 요구사항이 명확히 보입니다.

---

### 2. JWT Claim에 role 포함 → DB 조회 없이 권한 판단

```java
// JwtAuthenticationFilter.java
String role = jwtTokenProvider.getRole(token);
List<SimpleGrantedAuthority> authorities =
    List.of(new SimpleGrantedAuthority("ROLE_" + role));
```

매 요청마다 DB에서 사용자 역할을 조회하는 대신, JWT 토큰 클레임에서 직접 추출합니다.  
이는 MSA 환경이나 트래픽이 많은 경우 DB 조회 병목을 줄이는 데 유리합니다.  
단, 역할이 변경될 경우 기존 토큰의 만료까지 이전 권한으로 동작하므로, 실제 서비스에서는 토큰 블랙리스트 또는 짧은 만료 시간 정책을 함께 고려해야 합니다.

---

### 3. IDOR(Insecure Direct Object Reference) 방어

```java
// ResidentController.java
@GetMapping("/profile")
@PreAuthorize("hasRole('RESIDENT')")
public ResponseEntity<ResidentProfileResponse> getMyProfile(
        @AuthenticationPrincipal UserDetails userDetails) {  // ← URL 파라미터 X
    return ResponseEntity.ok(residentService.getMyProfile(userDetails.getUsername()));
}
```

`/api/resident//{id}` 처럼 ID를 URL에 노출하면 `resident1`이 `?id=2`를 조작하여  
다른 거주자의 프로필을 조회하는 IDOR 취약점이 생길 수 있습니다.  
대신 SecurityContext에서 인증된 username을 직접 사용하여 본인 데이터만 반환합니다.

---

### 4. ROLE_ 접두사 규칙

```java
// "ROLE_" 접두사 추가 → hasRole("ADMIN") 매핑에 필요
new SimpleGrantedAuthority("ROLE_" + role)
```

Spring Security의 `hasRole("ADMIN")`은 내부적으로 `ROLE_ADMIN` 권한을 찾습니다.  
JWT 클레임에는 "ADMIN"만 저장하고, 필터에서 "ROLE_" 접두사를 붙여 Spring Security 규칙에 맞춥니다.

---

### 5. 비밀번호 BCrypt 인코딩

```java
// BCryptPasswordEncoder 기본 strength=10
passwordEncoder.encode("admin123")
```

평문 저장 또는 단순 해시(MD5, SHA-1) 대신 BCrypt를 사용합니다.  
BCrypt는 salt를 자동 포함하고 cost factor 조정으로 브루트포스 저항성을 유지할 수 있습니다.

---

### 6. 응답 DTO에서 민감 정보 제거

```java
// UserResponse.from(user) → password 필드 미포함
public static UserResponse from(User user) {
    return new UserResponse(
        user.getId(), user.getUsername(), user.getName(),
        user.getEmail(), user.getRole(), user.getUnitNumber(), user.getCreatedAt()
    );
}
```

API 응답에 `password` 해시가 포함되지 않도록 Entity를 그대로 반환하지 않고  
전용 응답 DTO로 변환합니다.

---

## 실행 방법

### 사전 요구사항
- Java 17+
- Maven 3.6+

### 실행

```bash
git clone https://github.com/your-org/resident-rbac-demo.git
cd resident-rbac-demo
./mvnw spring-boot:run
```

애플리케이션이 시작되면 콘솔에 테스트 계정 정보가 출력됩니다.

```
=== 데모 데이터 초기화 완료 ===
ADMIN    : admin / admin123
MANAGER  : manager1 / manager123
RESIDENT : resident1 / resident123  (101호)
RESIDENT : resident2 / resident123  (202호)
```

### 테스트 실행

```bash
./mvnw test
```

---

## 테스트 시나리오

### 1. ADMIN 로그인 후 사용자 목록 조회

```bash
# 1) 로그인
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 2) 토큰으로 전체 사용자 조회
curl http://localhost:8080/api/admin/users \
  -H "Authorization: Bearer {발급된_토큰}"
```

### 2. RESIDENT 로그인 후 프로필 조회

```bash
# 1) 로그인
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"resident1","password":"resident123"}'

# 2) 본인 프로필 조회
curl http://localhost:8080/api/resident/profile \
  -H "Authorization: Bearer {발급된_토큰}"
```

### 3. 권한 없는 접근 시도 (403 확인)

```bash
# RESIDENT 토큰으로 ADMIN 전용 API 접근 시도 → 403
curl http://localhost:8080/api/admin/users \
  -H "Authorization: Bearer {resident_토큰}"
```

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Security | Spring Security 6, JWT (jjwt 0.11) |
| DB | H2 (In-Memory, 개발용) |
| ORM | Spring Data JPA (Hibernate) |
| Build | Maven |
| Test | JUnit 5, MockMvc |

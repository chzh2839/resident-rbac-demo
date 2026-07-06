package com.example.rbac;

import com.example.rbac.dto.LoginRequest;
import com.example.rbac.entity.AuditAction;
import com.example.rbac.entity.AuditLog;
import com.example.rbac.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC 통합 테스트
 *
 * 검증 시나리오:
 * 1. ADMIN은 /api/admin/users 접근 가능
 * 2. RESIDENT는 /api/admin/users 접근 불가 (403)
 * 3. RESIDENT는 /api/resident/profile 접근 가능
 * 4. ADMIN은 /api/resident/profile 접근 불가 (403) - 역할 분리
 * 5. 인증 없이 보호된 엔드포인트 접근 불가 (401/403)
 * 6. 잘못된 비밀번호로 로그인 실패 (401)
 */
@SpringBootTest
@AutoConfigureMockMvc
class RbacIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditLogRepository auditLogRepository;

    // ============================================================
    // 1. 로그인 테스트
    // ============================================================

    @Test
    @DisplayName("ADMIN 로그인 성공 → JWT 발급")
    void adminLoginSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("잘못된 비밀번호 → 401")
    void loginFail_wrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "wrongpassword")))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // 2. ADMIN 권한 테스트
    // ============================================================

    @Test
    @DisplayName("ADMIN → GET /api/admin/users 접근 성공")
    void adminCanAccessAdminUsers() throws Exception {
        String token = getToken("admin", "admin123");

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("RESIDENT → GET /api/admin/users 접근 불가 (403)")
    void residentCannotAccessAdminUsers() throws Exception {
        String token = getToken("resident1", "resident123");

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("MANAGER → GET /api/admin/users 접근 불가 (403)")
    void managerCannotAccessAdminUsers() throws Exception {
        String token = getToken("manager1", "manager123");

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // 3. RESIDENT 권한 테스트
    // ============================================================

    @Test
    @DisplayName("RESIDENT → GET /api/resident/profile 접근 성공")
    void residentCanAccessOwnProfile() throws Exception {
        String token = getToken("resident1", "resident123");

        mockMvc.perform(get("/api/resident/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitNumber").value("101호"));
    }

    @Test
    @DisplayName("ADMIN → GET /api/resident/profile 접근 불가 (403) - 역할 분리")
    void adminCannotAccessResidentProfile() throws Exception {
        String token = getToken("admin", "admin123");

        mockMvc.perform(get("/api/resident/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // 4. MANAGER 권한 테스트
    // ============================================================

    @Test
    @DisplayName("MANAGER → GET /api/manager/residents 접근 성공")
    void managerCanAccessResidentList() throws Exception {
        String token = getToken("manager1", "manager123");

        mockMvc.perform(get("/api/manager/residents")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("ADMIN → GET /api/manager/residents 접근 성공 (상위 역할)")
    void adminCanAccessManagerResidents() throws Exception {
        String token = getToken("admin", "admin123");

        mockMvc.perform(get("/api/manager/residents")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RESIDENT → GET /api/manager/residents 접근 불가 (403)")
    void residentCannotAccessManagerResidents() throws Exception {
        String token = getToken("resident1", "resident123");

        mockMvc.perform(get("/api/manager/residents")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // 5. 인증 없는 요청 테스트
    // ============================================================

    @Test
    @DisplayName("토큰 없이 보호된 엔드포인트 접근 → 403")
    void unauthenticatedRequestForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // 6. 감사 로그(Audit Log) 테스트
    // ============================================================

    @Test
    @DisplayName("로그인 성공 → LOGIN_SUCCESS 감사 로그 기록")
    void loginSuccessRecordsAuditLog() throws Exception {
        getToken("admin", "admin123");

        AuditLog latest = auditLogRepository.findAllByOrderByCreatedAtDescIdDesc().get(0);
        assertThat(latest.getAction()).isEqualTo(AuditAction.LOGIN_SUCCESS);
        assertThat(latest.getUsername()).isEqualTo("admin");
        assertThat(latest.getStatusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("로그인 실패 → LOGIN_FAILURE 감사 로그 기록")
    void loginFailureRecordsAuditLog() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "wrongpassword")))
                .andExpect(status().isUnauthorized());

        AuditLog latest = auditLogRepository.findAllByOrderByCreatedAtDescIdDesc().get(0);
        assertThat(latest.getAction()).isEqualTo(AuditAction.LOGIN_FAILURE);
        assertThat(latest.getUsername()).isEqualTo("admin");
        assertThat(latest.getStatusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("ADMIN → /api/admin/users 접근 성공 → ADMIN_API_ACCESS(200) 감사 로그 기록")
    void adminAccessRecordsAuditLogSuccess() throws Exception {
        String token = getToken("admin", "admin123");

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        AuditLog latest = auditLogRepository.findAllByOrderByCreatedAtDescIdDesc().get(0);
        assertThat(latest.getAction()).isEqualTo(AuditAction.ADMIN_API_ACCESS);
        assertThat(latest.getUsername()).isEqualTo("admin");
        assertThat(latest.getRequestUri()).isEqualTo("/api/admin/users");
        assertThat(latest.getStatusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("RESIDENT → /api/admin/users 접근 거부(403) → ADMIN_API_ACCESS(403) 감사 로그 기록")
    void deniedAdminAccessRecordsAuditLog() throws Exception {
        String token = getToken("resident1", "resident123");

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        AuditLog latest = auditLogRepository.findAllByOrderByCreatedAtDescIdDesc().get(0);
        assertThat(latest.getAction()).isEqualTo(AuditAction.ADMIN_API_ACCESS);
        assertThat(latest.getUsername()).isEqualTo("resident1");
        assertThat(latest.getStatusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("ADMIN → GET /api/admin/audit-logs 접근 성공")
    void adminCanAccessAuditLogs() throws Exception {
        String token = getToken("admin", "admin123");

        mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("MANAGER → GET /api/admin/audit-logs 접근 불가 (403)")
    void managerCannotAccessAuditLogs() throws Exception {
        String token = getToken("manager1", "manager123");

        mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // 7. Refresh Token / 로그아웃 테스트
    // ============================================================

    @Test
    @DisplayName("로그인 응답에 refreshToken 포함")
    void loginResponseIncludesRefreshToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    @DisplayName("유효한 refreshToken → /api/auth/refresh 성공, 새 accessToken 발급")
    void refreshWithValidTokenIssuesNewAccessToken() throws Exception {
        JsonNode loginBody = login("admin", "admin123");
        String originalAccessToken = loginBody.get("accessToken").asText();
        String originalRefreshToken = loginBody.get("refreshToken").asText();

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(originalRefreshToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode refreshBody = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        assertThat(refreshBody.get("accessToken").asText()).isNotEqualTo(originalAccessToken);
        assertThat(refreshBody.get("refreshToken").asText()).isNotEqualTo(originalRefreshToken);
    }

    @Test
    @DisplayName("이미 사용(회전)된 refreshToken 재사용 → 401")
    void reusedRefreshTokenRejected() throws Exception {
        JsonNode loginBody = login("admin", "admin123");
        String originalRefreshToken = loginBody.get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(originalRefreshToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(originalRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("알 수 없는 refreshToken → 401")
    void unknownRefreshTokenRejected() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson("garbage-token-value")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그아웃 → 이후 같은 accessToken으로 보호된 엔드포인트 접근 시 403")
    void logoutBlacklistsAccessToken() throws Exception {
        String token = getToken("admin", "admin123");

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("로그아웃 이후 같은 로그인 세션의 refreshToken도 거부됨")
    void logoutRevokesRefreshToken() throws Exception {
        JsonNode loginBody = login("admin", "admin123");
        String accessToken = loginBody.get("accessToken").asText();
        String refreshToken = loginBody.get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("토큰 없이 로그아웃 시도 → 403")
    void logoutWithoutTokenForbidden() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // Helper
    // ============================================================

    private String getToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, password)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private JsonNode login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String loginJson(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(
                new LoginRequestHelper(username, password));
    }

    private String refreshJson(String refreshToken) throws Exception {
        return objectMapper.writeValueAsString(new RefreshRequestHelper(refreshToken));
    }

    // 테스트용 내부 헬퍼 클래스 (record 사용)
    record LoginRequestHelper(String username, String password) {}

    record RefreshRequestHelper(String refreshToken) {}
}

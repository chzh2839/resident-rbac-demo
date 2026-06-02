package com.example.rbac;

import com.example.rbac.dto.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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

    private String loginJson(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(
                new LoginRequestHelper(username, password));
    }

    // 테스트용 내부 헬퍼 클래스 (record 사용)
    record LoginRequestHelper(String username, String password) {}
}

package com.example.rbac.service;

import com.example.rbac.dto.ResidentProfileResponse;
import com.example.rbac.entity.Role;
import com.example.rbac.entity.User;
import com.example.rbac.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * ResidentService 단위 테스트
 * UserRepository만 모킹해 서비스 로직을 격리 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ResidentServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ResidentService residentService;

    // ============================================================
    // 1. 프로필 조회 성공
    // ============================================================

    @Test
    @DisplayName("존재하는 username으로 조회 시 ResidentProfileResponse를 반환한다")
    void getMyProfile_returnsProfileWhenUserExists() {
        User user = User.builder()
                .id(1L)
                .username("resident1")
                .name("홍길동")
                .email("resident1@example.com")
                .role(Role.RESIDENT)
                .unitNumber("101호")
                .build();
        given(userRepository.findByUsername("resident1")).willReturn(Optional.of(user));

        ResidentProfileResponse response = residentService.getMyProfile("resident1");

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("홍길동");
        assertThat(response.getEmail()).isEqualTo("resident1@example.com");
        assertThat(response.getUnitNumber()).isEqualTo("101호");
        verify(userRepository).findByUsername("resident1");
    }

    // ============================================================
    // 2. 프로필 조회 실패
    // ============================================================

    @Test
    @DisplayName("존재하지 않는 username으로 조회 시 IllegalArgumentException 발생")
    void getMyProfile_throwsWhenUserNotFound() {
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> residentService.getMyProfile("ghost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용자를 찾을 수 없습니다.");
    }
}

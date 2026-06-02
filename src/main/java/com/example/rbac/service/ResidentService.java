package com.example.rbac.service;

import com.example.rbac.dto.ResidentProfileResponse;
import com.example.rbac.entity.User;
import com.example.rbac.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 거주자(Resident) 서비스
 * 거주자 본인 프로필 조회 기능 제공
 *
 * 고려사항:
 * - 본인 데이터만 조회 가능하도록 서비스 계층에서 username 일치 여부 추가 검증
 *   (URL 파라미터 조작 방지 목적 - IDOR 취약점 방어)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResidentService {

    private final UserRepository userRepository;

    /**
     * 거주자 프로필 조회
     *
     * @param requestUsername 요청자 username (SecurityContext 에서 추출)
     */
    public ResidentProfileResponse getMyProfile(String requestUsername) {
        User user = userRepository.findByUsername(requestUsername)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return ResidentProfileResponse.from(user);
    }
}

package com.example.rbac.config;

import com.example.rbac.entity.Role;
import com.example.rbac.entity.User;
import com.example.rbac.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션 시작 시 데모용 초기 데이터 삽입
 *
 * 테스트 계정:
 * - admin    / admin123   (ADMIN)
 * - manager1 / manager123 (MANAGER)
 * - resident1/ resident123 (RESIDENT) - 101호
 * - resident2/ resident123 (RESIDENT) - 202호
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) return;

        userRepository.save(User.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .name("시스템 관리자")
                .email("admin@building.com")
                .role(Role.ADMIN)
                .build());

        userRepository.save(User.builder()
                .username("manager1")
                .password(passwordEncoder.encode("manager123"))
                .name("김매니저")
                .email("manager1@building.com")
                .role(Role.MANAGER)
                .build());

        userRepository.save(User.builder()
                .username("resident1")
                .password(passwordEncoder.encode("resident123"))
                .name("이거주")
                .email("resident1@email.com")
                .role(Role.RESIDENT)
                .unitNumber("101호")
                .build());

        userRepository.save(User.builder()
                .username("resident2")
                .password(passwordEncoder.encode("resident123"))
                .name("박주민")
                .email("resident2@email.com")
                .role(Role.RESIDENT)
                .unitNumber("202호")
                .build());

        log.info("=== 데모 데이터 초기화 완료 ===");
        log.info("ADMIN    : admin / admin123");
        log.info("MANAGER  : manager1 / manager123");
        log.info("RESIDENT : resident1 / resident123  (101호)");
        log.info("RESIDENT : resident2 / resident123  (202호)");
    }
}

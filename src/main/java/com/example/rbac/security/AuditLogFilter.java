package com.example.rbac.security;

import com.example.rbac.service.AuditLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * ADMIN API 호출 감사 로그 필터
 *
 * 고려사항:
 * - /api/admin/** 요청에 대해서만 동작 (shouldNotFilter 로 그 외 경로는 건너뜀)
 * - FilterSecurityInterceptor 를 감싸는 위치(JwtAuthenticationFilter 바로 다음)에 등록해 인가 성공(2xx)뿐 아니라 URL 레벨에서 거부된 403도 함께 기록한다.
 *   ExceptionTranslationFilter 가 AccessDeniedException 을 내부에서 처리해 응답을 완성시키고 나서 제어가 돌아오므로,
 *   doFilter 호출 이후 response.getStatus() 를 읽으면 403 도 정확히 관측된다.
 *   @RestControllerAdvice 는 이 시점의 거부를 볼 수 없다.
 * - 인증 정보는 filterChain.doFilter 호출 "이전"에 캡처한다.
 *   이 필터가 실행되는 시점은 JwtAuthenticationFilter 바로 다음이라 AnonymousAuthenticationFilter 는 아직 실행 전이므로,
 *   토큰이 없거나 유효하지 않은 요청은 SecurityContext 의 Authentication 이 null 이다.
 *   이 경우 username 을 "anonymousUser" 로 기록한다.
 * - 다운스트림에서 처리되지 않은 예외가 발생하면(드묾) status 를 500 으로 기록하고
 *   원래 예외는 그대로 다시 던져 기존 동작을 바꾸지 않는다.
 */
@RequiredArgsConstructor
public class AuditLogFilter extends OncePerRequestFilter {

    private static final String ADMIN_PATH_PREFIX = "/api/admin";
    private static final String ANONYMOUS_USERNAME = "anonymousUser";

    private final AuditLogService auditLogService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(ADMIN_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String username = resolveUsername();
        boolean unhandledException = false;

        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException e) {
            unhandledException = true;
            throw e;
        } finally {
            int status = unhandledException ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : response.getStatus();
            auditLogService.recordAdminAccess(
                    username,
                    request.getMethod(),
                    request.getRequestURI(),
                    status,
                    request.getRemoteAddr()
            );
        }
    }

    private String resolveUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (authentication != null) ? authentication.getName() : ANONYMOUS_USERNAME;
    }
}

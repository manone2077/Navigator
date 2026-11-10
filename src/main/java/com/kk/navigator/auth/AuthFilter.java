package com.kk.navigator.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 启用访问密码后，拦截未登录的 /api 请求
 */
@Component
public class AuthFilter extends OncePerRequestFilter {
    /** 无需登录的接口 */
    private static final Set<String> PUBLIC = Set.of("/api/health", "/api/auth/status", "/api/auth/login", "/api/auth/logout");
    /** 开启访客浏览时，未登录也可访问的只读接口 */
    private static final Set<String> GUEST = Set.of("/api/all", "/api/tags");

    private final AuthService authService;

    public AuthFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !authService.isEnabled() || !path.startsWith("/api") || PUBLIC.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (authService.isLoggedIn(request)
                || (authService.isGuestView() && GUEST.contains(request.getRequestURI()))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"msg\":\"请先登录\",\"data\":null}");
    }
}

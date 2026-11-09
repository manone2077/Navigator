package com.kk.navigator.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 防止跨站请求伪造（CSRF）：所有修改类 /api 请求必须带 X-Requested-With: XMLHttpRequest。
 * <p>
 * 浏览器中的其它网站无法给跨站请求添加自定义请求头（会触发 CORS 预检，而本服务不允许跨域），
 * 因此 &lt;img&gt;、&lt;form&gt;、链接跳转等方式都无法触发删除、修改等操作。
 * 未设置访问密码时这一点尤其重要：否则内网中任何网页都能直接调用接口。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestGuardFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Requested-With";
    public static final String VALUE = "XMLHttpRequest";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || SAFE_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (VALUE.equalsIgnoreCase(request.getHeader(HEADER))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"msg\":\"请求被拒绝：缺少请求头 X-Requested-With\",\"data\":null}");
    }
}

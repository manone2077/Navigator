package com.kk.navigator.auth;

import com.kk.navigator.entity.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.info.BuildProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
public class AuthController {
    private final AuthService authService;
    private final String version;

    public AuthController(AuthService authService, ObjectProvider<BuildProperties> buildProperties) {
        this.authService = authService;
        BuildProperties props = buildProperties.getIfAvailable();
        this.version = props == null ? "dev" : props.getVersion();
    }

    /** 健康检查，同时返回版本号 */
    @GetMapping("/api/health")
    public R health() {
        return R.success("ok", Map.of("version", version));
    }

    @GetMapping("/api/auth/status")
    public R status(HttpServletRequest request) {
        return R.success("ok", Map.of(
                "enabled", authService.isEnabled(),
                "loggedIn", authService.isLoggedIn(request),
                "guestView", authService.isGuestView(),
                "version", version));
    }

    @PostMapping("/api/auth/login")
    public R login(@RequestBody Map<String, String> body, HttpServletRequest request, HttpServletResponse response) {
        if (!authService.isEnabled()) {
            return R.success("未启用访问密码");
        }
        String ip = clientIp(request);
        long wait = authService.lockedSeconds(ip);
        if (wait > 0) {
            return R.fail("密码错误次数过多，请 " + wait + " 秒后再试");
        }
        if (!authService.checkPassword(ip, body.get("password"))) {
            log.warn("login failed from {}", ip);
            return R.fail("密码错误");
        }
        boolean remember = !"false".equals(String.valueOf(body.get("remember")));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(request, authService.issueToken(),
                remember ? authService.tokenMaxAgeSeconds() : -1).toString());
        return R.success("登录成功");
    }

    @PostMapping("/api/auth/logout")
    public R logout(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(request, "", 0).toString());
        return R.success("已退出登录");
    }

    private static ResponseCookie cookie(HttpServletRequest request, String value, long maxAge) {
        boolean https = request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        return ResponseCookie.from(AuthService.COOKIE, value)
                .httpOnly(true)
                .secure(https)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    /**
     * 客户端 IP：只有请求来自本机的 nginx 时才信任它设置的 X-Real-IP，
     * 否则使用真实连接地址，防止伪造请求头绕过登录失败锁定
     */
    private static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String ip = request.getHeader("X-Real-IP");
        boolean fromLocalProxy = "127.0.0.1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote);
        return fromLocalProxy && ip != null && !ip.isBlank() ? ip.trim() : remote;
    }
}

package com.kk.navigator.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 访问密码认证。
 * <p>
 * 通过环境变量 NAV_PASSWORD 设置密码，未设置时不启用认证（与旧版本行为一致）。
 * NAV_GUEST_VIEW=true 时未登录用户可以浏览站点，但不能修改。
 * 登录后下发 HttpOnly Cookie，令牌为「过期时间.HMAC」，修改密码后旧令牌自动失效。
 */
@Service
public class AuthService {
    public static final String COOKIE = "nav_token";
    private static final long TOKEN_TTL_MS = 30L * 24 * 3600 * 1000;
    private static final int MAX_FAILURES = 5;
    private static final long LOCK_MS = 60 * 1000;

    private final String password;
    @Getter
    private final boolean guestView;
    private final Map<String, long[]> failures = new ConcurrentHashMap<>();

    public AuthService(@Value("${navigator.password:}") String password,
                       @Value("${navigator.guest-view:false}") boolean guestView) {
        this.password = password == null ? "" : password;
        this.guestView = guestView;
    }

    public boolean isEnabled() {
        return !password.isEmpty();
    }

    public boolean isLoggedIn(HttpServletRequest request) {
        if (!isEnabled()) {
            return true;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie c : cookies) {
            if (COOKIE.equals(c.getName()) && verify(c.getValue())) {
                return true;
            }
        }
        return false;
    }

    /** 登录失败过多时返回需要等待的秒数，否则返回 0 */
    public long lockedSeconds(String ip) {
        long[] f = failures.get(ip);
        if (f == null || f[0] < MAX_FAILURES) {
            return 0;
        }
        long left = f[1] + LOCK_MS - System.currentTimeMillis();
        if (left <= 0) {
            failures.remove(ip);
            return 0;
        }
        return (left + 999) / 1000;
    }

    public boolean checkPassword(String ip, String input) {
        boolean ok = input != null && MessageDigest.isEqual(
                input.getBytes(StandardCharsets.UTF_8), password.getBytes(StandardCharsets.UTF_8));
        if (ok) {
            failures.remove(ip);
        } else {
            failures.compute(ip, (k, v) -> v == null ? new long[]{1, System.currentTimeMillis()}
                    : new long[]{v[0] + 1, System.currentTimeMillis()});
        }
        return ok;
    }

    public String issueToken() {
        String expires = String.valueOf(System.currentTimeMillis() + TOKEN_TTL_MS);
        return expires + "." + sign(expires);
    }

    public int tokenMaxAgeSeconds() {
        return (int) (TOKEN_TTL_MS / 1000);
    }

    private boolean verify(String token) {
        int dot = token == null ? -1 : token.indexOf('.');
        if (dot <= 0) {
            return false;
        }
        String expires = token.substring(0, dot);
        try {
            if (Long.parseLong(expires) < System.currentTimeMillis()) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return MessageDigest.isEqual(sign(expires).getBytes(StandardCharsets.UTF_8),
                token.substring(dot + 1).getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(("navigator:" + password).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

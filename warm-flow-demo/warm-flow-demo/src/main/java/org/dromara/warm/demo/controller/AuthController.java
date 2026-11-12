package org.dromara.warm.demo.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.dromara.warm.demo.common.ApiResponse;
import org.dromara.warm.demo.config.DemoAuthInterceptor;
import org.dromara.warm.demo.config.DemoTokenService;
import org.dromara.warm.demo.config.DemoUserContext;
import org.dromara.warm.demo.dto.LoginRequest;
import org.dromara.warm.demo.service.UserService;
import org.dromara.warm.demo.vo.DemoUserVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Demo 登录接口：用户名 + 密码换取 HttpOnly 签名 Cookie。
 * <p>演示用户密码统一为 {@code warm-flow-demo}（可用环境变量 {@code WARM_DEMO_PASSWORD} 覆盖），
 * 生产请接入宿主自己的账号体系。</p>
 *
 * @author may
 * @since 2026/11/11
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final DemoTokenService tokenService;
    private final String password;

    public AuthController(UserService userService, DemoTokenService tokenService,
                          org.springframework.core.env.Environment environment) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.password = environment.getProperty("warm-flow-demo.password",
            environment.getProperty("WARM_DEMO_PASSWORD", "warm-flow-demo"));
    }

    /**
     * 登录：校验用户名密码，签发 HttpOnly 会话 Cookie。
     */
    @PostMapping("/login")
    public ApiResponse<DemoUserVo> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        DemoUserVo user = userService.listApprovers().stream()
            .filter(u -> u.getUserName().equals(request.getUsername()))
            .findFirst()
            .orElse(null);
        if (user == null || !MessageDigest.isEqual(
            password.getBytes(StandardCharsets.UTF_8),
            request.getPassword().getBytes(StandardCharsets.UTF_8))) {
            return ApiResponse.fail(401, "用户名或密码错误");
        }
        String token = tokenService.issue(user.getUserName());
        Cookie cookie = new Cookie(DemoAuthInterceptor.AUTH_COOKIE, token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) (tokenService.ttlMillis() / 1000));
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
        return ApiResponse.ok(user);
    }

    /**
     * 当前登录用户。
     */
    @GetMapping("/me")
    public ApiResponse<DemoUserVo> me(HttpServletRequest request) {
        String userName = DemoUserContext.get() != null
            ? DemoUserContext.get() : demoUserFromCookie(request);
        final String current = userName;
        DemoUserVo user = userService.listApprovers().stream()
            .filter(u -> u.getUserName().equals(current))
            .findFirst()
            .orElse(null);
        return user == null ? ApiResponse.fail(401, "未登录") : ApiResponse.ok(user);
    }

    /**
     * 登出：清除会话 Cookie。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie(DemoAuthInterceptor.AUTH_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ApiResponse.ok(null);
    }

    /**
     * 登录用户列表（登录页展示）。
     */
    @GetMapping("/users")
    public ApiResponse<List<DemoUserVo>> users() {
        return ApiResponse.ok(userService.listApprovers());
    }

    private String demoUserFromCookie(HttpServletRequest request) {
        String token = DemoAuthInterceptor.cookieValue(request);
        return tokenService.verify(token);
    }
}

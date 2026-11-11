package org.dromara.warm.demo.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * 统一认证拦截器：从登录 Cookie 解析用户写入 {@link DemoUserContext}，未认证返回 401。
 * <p>
 * 覆盖业务接口 {@code /api/**} 与设计器数据接口 {@code /warm-flow/**}；白名单仅登录相关接口与
 * 设计器静态资源。身份只来自签名 Cookie，{@code X-User-Name} 请求头不再作为身份来源。
 * 设计器接口的业务裁决由 {@link DemoDesignerAuthHandler}（SDK SPI）完成，本拦截器只负责
 * 把 Cookie 里的身份解析进上下文并拒绝未登录请求。
 *
 * @author may
 * @since 2026/11/11
 */
@Component
public class DemoAuthInterceptor implements HandlerInterceptor {

    /**
     * 登录 Cookie 名称。
     */
    public static final String AUTH_COOKIE = "WF_DEMO_SESSION";

    /**
     * 无需登录的路径（精确前缀匹配）：登录接口、登录页用户列表（只读、仅演示用户名）、设计器静态资源。
     */
    private static final Set<String> PUBLIC_PATHS = Set.of("/api/auth/login", "/api/auth/users", "/warm-flow-ui/");

    private final DemoTokenService tokenService;

    public DemoAuthInterceptor(DemoTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        if (request.getMethod().equals("OPTIONS")) {
            return true;
        }
        String path = request.getRequestURI();
        for (String prefix : PUBLIC_PATHS) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        String user = tokenService.verify(cookieValue(request));
        if (StringUtils.isEmpty(user)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已过期\"}");
            return false;
        }
        DemoUserContext.set(user.trim());
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        DemoUserContext.clear();
    }

    /**
     * 从请求 Cookie 中取登录令牌。
     */
    public static String cookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AUTH_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}

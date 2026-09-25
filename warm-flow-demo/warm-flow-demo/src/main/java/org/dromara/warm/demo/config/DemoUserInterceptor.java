package org.dromara.warm.demo.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 把请求头 {@code X-User-Name} 写入 {@link DemoUserContext}，并在请求结束后清理。
 *
 * @author may
 */
public class DemoUserInterceptor implements HandlerInterceptor {

    /**
     * 当前用户名请求头。
     */
    private static final String USER_HEADER = "X-User-Name";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String userName = request.getHeader(USER_HEADER);
        DemoUserContext.set(StringUtils.isEmpty(userName) ? null : userName.trim());
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                @NonNull Object handler, @Nullable Exception ex) {
        DemoUserContext.clear();
    }
}

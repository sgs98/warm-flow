package org.dromara.warm.demo.config;

import lombok.RequiredArgsConstructor;
import org.dromara.warm.demo.service.UserService;
import org.dromara.warm.flow.core.handler.PermissionHandler;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Demo 流程权限处理器。
 * <p>权限来自当前 HTTP 请求头 {@code X-User-Name} 对应的 Demo 用户：
 * 引擎办理权限校验读取 {@link #permissions()}，审计字段填充读取 {@link #getHandler()}，
 * 请求进入与结束由 {@link DemoUserInterceptor} 写入、清理 {@link DemoUserContext}。</p>
 *
 * @author may
 */
@Component
@RequiredArgsConstructor
public class DemoPermissionHandler implements PermissionHandler {

    private final UserService userService;

    /**
     * 获取当前请求用户的权限标识集合。
     *
     * @return 权限标识集合；请求未携带用户时返回空集合
     */
    @Override
    public List<String> permissions() {
        String userName = DemoUserContext.get();
        if (StringUtils.isEmpty(userName)) {
            return Collections.emptyList();
        }
        return userService.permissionFlags(userName);
    }

    /**
     * 获取当前请求用户。
     *
     * @return 当前请求用户名；请求未携带用户时返回 {@code null}
     */
    @Override
    public String getHandler() {
        return DemoUserContext.get();
    }
}

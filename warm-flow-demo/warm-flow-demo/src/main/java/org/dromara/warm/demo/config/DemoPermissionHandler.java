package org.dromara.warm.demo.config;

import lombok.RequiredArgsConstructor;
import org.dromara.warm.demo.service.UserService;
import org.dromara.warm.demo.vo.DemoUserVo;
import org.dromara.warm.flow.core.spi.PermissionHandler;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * 设计器办理人选择返回的用户类型前缀。
     */
    private static final String USER_PREFIX = "user:";

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

    /**
     * 将设计器保存的带类型前缀的办理人标识归一化为唯一办理人标识。
     * <p>节点办理人选择返回 {@code user:approver1} 这类入库标识，而办理人（{@link #getHandler()}）
     * 是用户名本身；不归一化时 flow_user.processed_by 与办理人不一致，同一办理人自动审批等按办理人
     * 比对的逻辑无法命中。部门等其它前缀标识保持原样，仍由节点权限匹配消费。</p>
     *
     * @param permissions 设计器中预设的权限标识
     * @return 归一化后的权限标识
     */
    @Override
    public List<String> convertPermissions(List<String> permissions) {
        if (CollUtil.isEmpty(permissions)) {
            return permissions;
        }
        Set<String> userNames = userService.listApprovers().stream()
            .map(DemoUserVo::getUserName).collect(Collectors.toSet());
        return permissions.stream().map(permission -> normalize(permission, userNames))
            .collect(Collectors.toList());
    }

    /**
     * 去掉用户类型前缀，仅当其余部分确实是 demo 用户时才转换，避免误改其它业务标识。
     *
     * @param permission 权限标识
     * @param userNames  demo 用户名校验集合
     * @return 归一化后的权限标识
     */
    private String normalize(String permission, Set<String> userNames) {
        if (StringUtils.isEmpty(permission) || !permission.startsWith(USER_PREFIX)) {
            return permission;
        }
        String userName = permission.substring(USER_PREFIX.length());
        return userNames.contains(userName) ? userName : permission;
    }

    /**
     * 解析抄送人：把设计器配置的标识展开成具体用户。
     * <p>抄送不参与办理，收件人必须是人：{@code user:approver1} 取该用户，{@code dept:001} 展开成
     * 部门内全部用户，裸用户名按用户处理；都匹配不上（角色、未知编码等）的标识直接丢弃，
     * 引擎不会把这些字符串写进抄送留痕。</p>
     *
     * @param handlers 节点上配置的抄送人标识
     * @return 具体用户名集合；空集合表示一个都落不到人
     */
    @Override
    public List<String> resolveCopyHandlers(List<String> handlers) {
        if (CollUtil.isEmpty(handlers)) {
            return new ArrayList<>();
        }
        return userService.listByPermissionFlags(handlers).stream()
            .map(DemoUserVo::getUserName)
            .distinct()
            .collect(Collectors.toList());
    }
}

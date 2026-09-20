package org.dromara.warm.demo.listener;

import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.invoker.FrameInvoker;
import org.dromara.warm.flow.core.listener.GlobalListener;
import org.dromara.warm.flow.core.listener.ListenerVariable;
import org.dromara.warm.demo.service.UserService;
import org.dromara.warm.demo.vo.DemoUserVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * demo 全局流程监听器。
 *
 * @author may
 * @since 2026/9/11
 */
public class DemoGlobalListener implements GlobalListener {

    /** Demo 全局监听器日志。 */
    private static final Logger log = LoggerFactory.getLogger(DemoGlobalListener.class);

    /**
     * 任务开始时记录全局监听器触发信息。
     *
     * @param listenerVariable 监听器变量
     */
    @Override
    public void start(ListenerVariable listenerVariable) {
        logEvent("start", listenerVariable);
    }

    /**
     * 按节点编码应用办理人映射，支持并行/条件分支下不同节点选择不同办理人。
     *
     * @param listenerVariable 监听器变量
     */
    @Override
    public void assignment(ListenerVariable listenerVariable) {
        logEvent("assignment", listenerVariable);
        Map<String, Object> variable = listenerVariable.getVariable();
        if (listenerVariable.getNextTasks() == null) {
            return;
        }
        UserService userService = FrameInvoker.getBean(UserService.class);
        for (Task task : listenerVariable.getNextTasks()) {
            Object handlers = variable == null ? null
                : variable.get(SkipType.PASS.getKey() + ":" + task.getNodeCode());
            List<String> permissionFlags = handlers == null
                ? task.getPermissionList() : splitHandlers(handlers);
            if (userService == null || permissionFlags == null || permissionFlags.isEmpty()) {
                continue;
            }
            task.setPermissionList(resolveUserNames(userService, permissionFlags));
        }
    }

    /**
     * 当前任务完成时记录全局监听器触发信息。
     *
     * @param listenerVariable 监听器变量
     */
    @Override
    public void finish(ListenerVariable listenerVariable) {
        logEvent("finish", listenerVariable);
    }

    /**
     * 后续任务创建时记录全局监听器触发信息。
     *
     * @param listenerVariable 监听器变量
     */
    @Override
    public void create(ListenerVariable listenerVariable) {
        logEvent("create", listenerVariable);
    }

    /**
     * 输出全局监听器的通用上下文，发起阶段实例可能尚未创建，因此统一判空。
     *
     * @param event 监听事件
     * @param listenerVariable 监听器变量
     */
    private void logEvent(String event, ListenerVariable listenerVariable) {
        Instance instance = listenerVariable == null ? null : listenerVariable.getInstance();
        Node node = listenerVariable == null ? null : listenerVariable.getNode();
        Task task = listenerVariable == null ? null : listenerVariable.getTask();
        log.info("Demo 全局监听器触发: event={}, instanceId={}, nodeCode={}, taskId={}", event,
            instance == null ? null : instance.getId(),
            node == null ? null : node.getNodeCode(),
            task == null ? null : task.getId());
    }

    /**
     * 将流程变量中的办理人字符串拆分为权限标识。
     *
     * @param handlers 办理人变量
     * @return 权限标识集合
     */
    private List<String> splitHandlers(Object handlers) {
        return Arrays.stream(String.valueOf(handlers).split(","))
            .map(String::trim)
            .filter(handler -> !handler.isEmpty())
            .collect(Collectors.toList());
    }

    /**
     * 将设计器权限标识展开为实际用户名，避免权限前缀落入 flow_user.processed_by。
     * 部门权限需要展开为部门下的用户，不能简单截去 dept: 前缀。
     *
     * @param userService demo 用户服务
     * @param permissionFlags 设计器权限标识
     * @return 实际用户名集合
     */
    private List<String> resolveUserNames(UserService userService, List<String> permissionFlags) {
        List<DemoUserVo> users = userService.listByPermissionFlags(permissionFlags);
        if (users == null || users.isEmpty()) {
            return new ArrayList<>();
        }
        return users.stream()
            .map(DemoUserVo::getUserName)
            .filter(userName -> userName != null && !userName.trim().isEmpty())
            .map(String::trim)
            .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new),
                ArrayList::new));
    }
}

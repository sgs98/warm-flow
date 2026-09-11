package org.dromara.warm.demo.listener;

import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.listener.GlobalListener;
import org.dromara.warm.flow.core.listener.ListenerVariable;

import java.util.Arrays;
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

    /**
     * 按节点编码应用办理人映射，支持并行/条件分支下不同节点选择不同办理人。
     *
     * @param listenerVariable 监听器变量
     */
    @Override
    public void assignment(ListenerVariable listenerVariable) {
        Map<String, Object> variable = listenerVariable.getVariable();
        if (variable == null || listenerVariable.getNextTasks() == null) {
            return;
        }
        for (Task task : listenerVariable.getNextTasks()) {
            Object handlers = variable.get(SkipType.PASS.getKey() + ":" + task.getNodeCode());
            if (handlers == null) {
                continue;
            }
            List<String> permissionList = Arrays.stream(String.valueOf(handlers).split(","))
                .map(String::trim)
                .filter(handler -> !handler.isEmpty())
                .collect(Collectors.toList());
            if (!permissionList.isEmpty()) {
                task.setPermissionList(permissionList);
            }
        }
    }
}

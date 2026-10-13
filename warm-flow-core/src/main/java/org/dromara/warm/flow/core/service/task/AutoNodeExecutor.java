package org.dromara.warm.flow.core.service.task;

import org.dromara.warm.flow.core.entity.Node;

/**
 * 自动节点执行器扩展点。
 *
 * <p>只负责"节点干什么"：解析配置、选处理器、执行、写变量。
 * 临时待办生命周期、历史、后继路由与循环保护由执行链统一负责。</p>
 *
 * @author warm
 */
public interface AutoNodeExecutor {

    /**
     * 是否处理该节点。
     *
     * @param node 流程节点
     * @return 处理时返回 {@code true}
     */
    boolean supports(Node node);

    /**
     * 执行自动节点，业务结果通过上下文变量返回。
     *
     * @param context 自动节点上下文
     */
    void execute(AutoNodeContext context);
}

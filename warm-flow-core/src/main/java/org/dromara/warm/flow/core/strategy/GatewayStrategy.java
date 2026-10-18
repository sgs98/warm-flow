package org.dromara.warm.flow.core.strategy;

import org.dromara.warm.flow.core.entity.Skip;

import java.util.List;
import java.util.Map;

/**
 * 网关出口选择策略。
 * <p>
 * 网关节点解析时，节点服务会按网关节点类型选择一个策略，并把当前网关的所有出口线交给策略裁剪。
 * 策略只负责选择本次实际生效的出口，不负责创建任务、写历史或移动实例状态。
 *
 * @author may
 */
public interface GatewayStrategy {

    /**
     * 判断策略是否支持指定网关类型。
     *
     * @param nodeType 网关节点类型，取值来自 {@code NodeType}
     * @return 支持该网关类型时返回 {@code true}
     */
    boolean supports(Integer nodeType);

    /**
     * 根据网关类型选择实际生效的出口。
     * <p>
     * 返回空集合表示没有可用出口，调用方会按条件未命中处理。
     *
     * @param skips    当前网关的候选出口线
     * @param variable 流程变量，用于条件表达式判断
     * @return 本次流转实际生效的出口线
     */
    List<Skip> select(List<Skip> skips, Map<String, Object> variable);
}

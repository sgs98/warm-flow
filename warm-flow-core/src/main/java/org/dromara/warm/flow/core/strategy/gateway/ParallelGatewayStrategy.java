package org.dromara.warm.flow.core.strategy.gateway;

import org.dromara.warm.flow.core.entity.Skip;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.strategy.GatewayStrategy;

import java.util.List;
import java.util.Map;

/**
 * 并行网关出口选择策略。
 * <p>
 * 并行网关不做条件判断，所有出口线都会生效。分叉时会为每个出口生成后续路径；
 * 汇聚等待由路径解析器根据前置活动任务判断。
 *
 * @author may
 */
public class ParallelGatewayStrategy implements GatewayStrategy {

    /**
     * 并行网关策略只处理并行网关节点。
     *
     * @param nodeType 网关节点类型
     * @return 是并行网关时返回 {@code true}
     */
    @Override
    public boolean supports(Integer nodeType) {
        return NodeType.isGateWayParallel(nodeType);
    }

    /**
     * 返回并行网关的全部出口线。
     *
     * @param skips    当前并行网关的候选出口线
     * @param variable 流程变量，当前策略不使用
     * @return 全部候选出口线
     */
    @Override
    public List<Skip> select(List<Skip> skips, Map<String, Object> variable) {
        return skips;
    }
}

package org.dromara.warm.flow.core.strategy.gateway;

import org.dromara.warm.flow.core.entity.Skip;
import org.dromara.warm.flow.core.enums.NodeType;

import java.util.List;
import java.util.Map;

/**
 * 并行网关出口选择策略。
 *
 * @author may
 */
public class ParallelGatewayStrategy implements GatewayStrategy {

    @Override
    public boolean supports(Integer nodeType) {
        return NodeType.isGateWayParallel(nodeType);
    }

    @Override
    public List<Skip> select(List<Skip> skips, Map<String, Object> variable) {
        return skips;
    }
}

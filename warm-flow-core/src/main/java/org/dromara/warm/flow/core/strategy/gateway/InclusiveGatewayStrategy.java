package org.dromara.warm.flow.core.strategy.gateway;

import org.dromara.warm.flow.core.entity.Skip;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.utils.ExpressionUtil;
import org.dromara.warm.flow.core.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 包容网关出口选择策略。
 *
 * <p>有条件出口命中时只执行命中出口；无条件出口只在所有条件均未命中时兜底。</p>
 *
 * @author may
 */
public class InclusiveGatewayStrategy implements GatewayStrategy {

    @Override
    public boolean supports(Integer nodeType) {
        return NodeType.isGateWayInclusive(nodeType);
    }

    @Override
    public List<Skip> select(List<Skip> skips, Map<String, Object> variable) {
        List<Skip> matched = new ArrayList<>();
        List<Skip> defaults = new ArrayList<>();
        for (Skip skip : skips) {
            if (StringUtils.isEmpty(skip.getSkipCondition())) {
                defaults.add(skip);
            } else if (ExpressionUtil.evalCondition(skip.getSkipCondition(), variable)) {
                matched.add(skip);
            }
        }
        return matched.isEmpty() ? defaults : matched;
    }
}

package org.dromara.warm.flow.core.strategy.gateway;

import org.dromara.warm.flow.core.entity.Skip;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.ExpressionUtil;
import org.dromara.warm.flow.core.utils.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 排他网关出口选择策略。
 *
 * @author may
 */
public class SerialGatewayStrategy implements GatewayStrategy {

    @Override
    public boolean supports(Integer nodeType) {
        return NodeType.isGateWaySerial(nodeType);
    }

    @Override
    public List<Skip> select(List<Skip> skips, Map<String, Object> variable) {
        Skip defaultSkip = null;
        for (Skip skip : skips) {
            if (StringUtils.isEmpty(skip.getSkipCondition())) {
                if (defaultSkip == null) {
                    defaultSkip = skip;
                }
            } else if (ExpressionUtil.evalCondition(skip.getSkipCondition(), variable)) {
                return Collections.singletonList(skip);
            }
        }
        return defaultSkip == null ? Collections.<Skip>emptyList() : CollUtil.toList(defaultSkip);
    }
}

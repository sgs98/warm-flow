package org.dromara.warm.flow.core.strategy.gateway;

import org.dromara.warm.flow.core.entity.Skip;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.ExpressionUtil;
import org.dromara.warm.flow.core.utils.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 排他网关出口选择策略。
 * <p>
 * 排他网关一次只允许一条出口生效：优先返回第一条条件命中的出口；
 * 若没有条件命中，则返回第一条无条件出口作为默认出口；若两者都不存在则返回空集合。
 *
 * @author may
 */
public class SerialGatewayStrategy implements GatewayStrategy {

    /**
     * 排他网关策略只处理排他网关节点。
     *
     * @param nodeType 网关节点类型
     * @return 是排他网关时返回 {@code true}
     */
    @Override
    public boolean supports(Integer nodeType) {
        return NodeType.isGateWaySerial(nodeType);
    }

    /**
     * 选择排他网关的唯一生效出口。
     * <p>
     * 遍历时记录第一条无条件出口作为默认出口；遇到第一条条件表达式命中的出口立即返回，
     * 不再继续判断后续出口。
     *
     * @param skips    当前排他网关的候选出口线
     * @param variable 流程变量
     * @return 至多一条生效出口线
     */
    @Override
    public List<Skip> select(List<Skip> skips, Map<String, Object> variable) {
        Skip defaultSkip = null;
        for (Skip skip : skips) {
            if (StringUtils.isEmpty(skip.getSkipCondition())) {
                if (defaultSkip == null) {
                    defaultSkip = skip;
                }
            } else if (ExpressionUtil.evalCondition(skip.getSkipCondition(), variable)) {
                return List.of(skip);
            }
        }
        return defaultSkip == null ? List.of() : CollUtil.toList(defaultSkip);
    }
}

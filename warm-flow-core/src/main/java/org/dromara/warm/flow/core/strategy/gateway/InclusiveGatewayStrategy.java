package org.dromara.warm.flow.core.strategy.gateway;

import org.dromara.warm.flow.core.entity.Skip;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.strategy.GatewayStrategy;
import org.dromara.warm.flow.core.utils.ExpressionUtil;
import org.dromara.warm.flow.core.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 包容网关出口选择策略。
 * <p>
 * 包容网关允许一次流转命中多条出口：无条件出口始终生效，有条件出口仅在条件表达式命中时生效。
 * 调用方会继续递归解析每条命中出口的后续路径。
 *
 * @author may
 */
public class InclusiveGatewayStrategy implements GatewayStrategy {

    /**
     * 包容网关策略只处理包容网关节点。
     *
     * @param nodeType 网关节点类型
     * @return 是包容网关时返回 {@code true}
     */
    @Override
    public boolean supports(Integer nodeType) {
        return NodeType.isGateWayInclusive(nodeType);
    }

    /**
     * 选择包容网关的所有生效出口。
     * <p>
     * 未配置条件的出口作为默认出口直接加入结果；配置条件的出口通过
     * {@link ExpressionUtil#evalCondition(String, Map)} 判断，命中后加入结果。
     *
     * @param skips    当前包容网关的候选出口线
     * @param variable 流程变量
     * @return 本次流转生效的出口线集合
     */
    @Override
    public List<Skip> select(List<Skip> skips, Map<String, Object> variable) {
        List<Skip> matched = new ArrayList<>();
        for (Skip skip : skips) {
            if (StringUtils.isEmpty(skip.getSkipCondition())
                || ExpressionUtil.evalCondition(skip.getSkipCondition(), variable)) {
                matched.add(skip);
            }
        }
        return matched;
    }
}

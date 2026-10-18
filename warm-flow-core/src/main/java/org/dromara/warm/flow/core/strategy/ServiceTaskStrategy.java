package org.dromara.warm.flow.core.strategy;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务任务表达式策略接口。
 *
 * @author may
 */
public interface ServiceTaskStrategy extends ExpressionStrategy<Object> {

    /**
     * 服务任务表达式策略实现集合，后注册优先匹配。
     */
    List<ExpressionStrategy<Object>> EXPRESSION_STRATEGY_LIST = new ArrayList<>();

    @Override
    default void setExpression(ExpressionStrategy<Object> strategy) {
        EXPRESSION_STRATEGY_LIST.add(strategy);
    }
}

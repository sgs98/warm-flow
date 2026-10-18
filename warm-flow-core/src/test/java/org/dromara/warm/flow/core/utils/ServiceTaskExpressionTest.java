package org.dromara.warm.flow.core.utils;

import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.strategy.ServiceTaskStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceTaskExpressionTest {

    @AfterEach
    void tearDown() {
        ServiceTaskStrategy.EXPRESSION_STRATEGY_LIST.clear();
    }

    @Test
    void evalViaRegisteredStrategy() {
        ServiceTaskStrategy.EXPRESSION_STRATEGY_LIST.add(new ServiceTaskStrategy() {
            @Override
            public String getType() {
                return "#";
            }

            @Override
            public Object eval(String expression, Map<String, Object> variable) {
                return "ok:" + expression;
            }
        });
        assertEquals("ok:#{@x}", ExpressionUtil.evalServiceTask("#{@x}", Map.of()));
    }

    @Test
    void noStrategyThrows() {
        assertThrows(FlowException.class, () -> ExpressionUtil.evalServiceTask("#{@x}", Map.of()));
    }
}

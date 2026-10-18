package org.dromara.warm.plugin.modes.sb.expression;

import org.dromara.warm.flow.core.spi.ServiceTaskStrategy;
import org.dromara.warm.plugin.modes.sb.helper.SpelHelper;

import java.util.Map;

/**
 * 服务任务表达式spel: #{@bean.method(#execution)}
 *
 * @author warm
 */
public class ServiceTaskStrategySpel implements ServiceTaskStrategy {

    @Override
    public String getType() {
        return "#";
    }

    @Override
    public Object eval(String expression, Map<String, Object> variable) {
        return SpelHelper.parseExpression(expression, variable);
    }
}

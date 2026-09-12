package org.dromara.warm.flow.core.strategy.gateway;

import org.dromara.warm.flow.core.entity.Skip;

import java.util.List;
import java.util.Map;

/**
 * 网关出口选择策略。
 *
 * @author may
 */
public interface GatewayStrategy {

    /**
     * 判断策略是否支持指定网关类型。
     *
     * @param nodeType 网关节点类型
     * @return 是否支持
     */
    boolean supports(Integer nodeType);

    /**
     * 根据网关类型选择实际生效的出口。
     *
     * @param skips    网关出口
     * @param variable 流程变量
     * @return 生效出口
     */
    List<Skip> select(List<Skip> skips, Map<String, Object> variable);
}

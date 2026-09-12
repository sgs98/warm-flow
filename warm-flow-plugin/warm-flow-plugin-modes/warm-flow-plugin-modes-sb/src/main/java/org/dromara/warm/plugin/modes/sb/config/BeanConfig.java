/*
 *    Copyright 2024-2025, Warm-Flow (290631660@qq.com).
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.dromara.warm.plugin.modes.sb.config;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.config.WarmFlow;
import org.dromara.warm.flow.core.enums.FrameworkType;
import org.dromara.warm.flow.core.invoker.FrameInvoker;
import org.dromara.warm.flow.core.service.ChartService;
import org.dromara.warm.flow.core.service.impl.ChartServiceImpl;
import org.dromara.warm.flow.core.utils.ExpressionUtil;
import org.dromara.warm.plugin.modes.sb.expression.*;
import org.dromara.warm.plugin.modes.sb.helper.SpelHelper;
import org.dromara.warm.plugin.modes.sb.utils.SpringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import java.util.Objects;

/**
 * 工作流bean注册配置
 *
 * @author warm
 * @since 2023/6/5 23:01
 */
@SuppressWarnings("rawtypes unchecked")
@Import({SpringUtil.class, SpelHelper.class})
@ConditionalOnProperty(value = "warm-flow.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WarmFlowProperties.class)
public class BeanConfig {

    private static final Logger log = LoggerFactory.getLogger(BeanConfig.class);

    @Bean
    public ChartService chartService() {
        return new ChartServiceImpl();
    }

    @Bean
    public WarmFlow initFlow() {
        setNewEntity();
        FrameInvoker.setCfgFunction((key) -> Objects.requireNonNull(SpringUtil.getBean(Environment.class)).getProperty(key));
        FrameInvoker.setBeanFunction(SpringUtil::getBean);
        WarmFlowProperties warmFlow = SpringUtil.getBean(WarmFlowProperties.class);
        warmFlow.init();
        warmFlow.setFramework(FrameworkType.SPRING_BOOT);
        FlowEngine.setFlowConfig(warmFlow);
        setExpression();
        after(warmFlow);
        log.info("【warm-flow】，加载完成");
        return warmFlow;
    }

    private void setExpression() {
        ExpressionUtil.setExpression(new ConditionStrategyDefault());
        ExpressionUtil.setExpression(new ConditionStrategySpel());
        ExpressionUtil.setExpression(new ListenerStrategySpel());
        ExpressionUtil.setExpression(new HandlerStrategySpel());
        ExpressionUtil.setExpression(new VoteSignStrategyDefault());
        ExpressionUtil.setExpression(new VoteSignStrategySpel());
    }

    public void setNewEntity() {
    }

    public void after(WarmFlow flowConfig) {
    }
}

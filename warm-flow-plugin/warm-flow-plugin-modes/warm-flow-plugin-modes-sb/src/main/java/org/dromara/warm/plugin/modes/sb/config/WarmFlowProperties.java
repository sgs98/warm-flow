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

import lombok.Getter;
import lombok.Setter;
import org.dromara.warm.flow.core.config.WarmFlow;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Lion Li
 */
@ConfigurationProperties("warm-flow")
public class WarmFlowProperties extends WarmFlow {

    /**
     * SpEL 精确 Bean 名与可调用方法的白名单。默认不允许访问任何 Bean；
     * 空方法集合只允许引用该 Bean（用于服务任务 delegateExpression）。
     * 授权方法名包含其所有重载，使用方必须审查入口及返回值，不得授权容器或基础设施代理。
     */
    @Getter
    @Setter
    private Map<String, Set<String>> spelAllowedBeanMethods = new LinkedHashMap<>();
}

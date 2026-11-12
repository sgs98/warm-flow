/*
 *    Copyright 2026, may (1742057357@qq.com).
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
package org.dromara.warm.flow.ui.config;

import org.dromara.warm.flow.ui.controller.DesignerAuthExceptionHandler;
import org.dromara.warm.flow.ui.controller.WarmFlowController;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 仅加载自动配置，不依赖宿主对 SDK 包的组件扫描。
 *
 * @author warm
 */
class WarmFlowUiConfigTest {

    @Test
    void enabledRegistersControllerAndAuthAdviceWithoutComponentScan() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(WarmFlowUiConfig.class)) {
            assertNotNull(context.getBean(WarmFlowController.class));
            assertNotNull(context.getBean(DesignerAuthExceptionHandler.class));
        }
    }

    @Test
    void disabledRegistersNeitherControllerNorAuthAdvice() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("test", Map.of("warm-flow.ui", "false")));
            context.register(WarmFlowUiConfig.class);
            context.refresh();
            assertTrue(context.getBeansOfType(WarmFlowController.class).isEmpty());
            assertTrue(context.getBeansOfType(DesignerAuthExceptionHandler.class).isEmpty());
        }
    }
}

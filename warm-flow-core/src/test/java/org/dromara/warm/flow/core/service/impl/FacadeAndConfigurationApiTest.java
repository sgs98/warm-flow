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
package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.config.WarmFlow;
import org.dromara.warm.flow.core.dto.PathWayData;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.enums.ChartStatus;
import org.dromara.warm.flow.core.json.JsonConvert;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.utils.IdUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FlowEngine 门面、图表服务、配置和内置 ID 生成器公共 API 覆盖。
 *
 * @author warm
 */
class FacadeAndConfigurationApiTest {

    private FlowTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new FlowTestHarness();
    }

    @AfterEach
    void tearDown() {
        harness.close();
    }

    @Test
    void flowEngineSuppliersAndHandlers_arePubliclyReplaceable() {
        assertNotNull(FlowEngine.newDef());
        assertNotNull(FlowEngine.newNode());
        assertNotNull(FlowEngine.newSkip());
        assertNotNull(FlowEngine.newIns());
        assertNotNull(FlowEngine.newTask());
        assertNotNull(FlowEngine.newHisTask());
        assertNotNull(FlowEngine.newUser());
        assertNotNull(FlowEngine.newForm());
        assertNotNull(FlowEngine.defService());
        assertNotNull(FlowEngine.nodeService());
        assertNotNull(FlowEngine.skipService());
        assertNotNull(FlowEngine.insService());
        assertNotNull(FlowEngine.taskService());
        assertNotNull(FlowEngine.hisTaskService());
        assertNotNull(FlowEngine.userService());
        assertNotNull(FlowEngine.formService());
        assertNotNull(FlowEngine.chartService());
        assertNotNull(FlowEngine.workflow());
        assertNotNull(FlowEngine.dataFillHandler());
        assertNotNull(FlowEngine.getFlowConfig());
        WarmFlow flowConfig = new WarmFlow();
        flowConfig.setDataSourceType("mysql");
        assertEquals("mysql", flowConfig.getDataSourceType());
    }

    @Test
    void chartService_coversRgbAndStartMetadata() {
        Definition definition = TestFlows.serialFlow("chart-api");
        assertEquals(3, FlowEngine.chartService().getChartRgb(null).size());
        PathWayData pathWayData = new PathWayData().setDefId(definition.getId());
        String metadata = FlowEngine.chartService().startMetadata(pathWayData);
        assertTrue(metadata.contains("chart-api"));
    }

    @Test
    void warmFlowInitAndChartCustomColor_keepConfigurationContract() {
        WarmFlow config = new WarmFlow();
        config.setBanner(false);
        config.setChartStatusColor(List.of("1,2,3", "4,5,6", "7,8,9"));
        FlowEngine.setFlowConfig(config);
        JsonConvert jsonConvert = FlowEngine.jsonConvert;
        try {
            config.init();
            assertEquals(config, FlowEngine.getFlowConfig());
            assertEquals(7, ChartStatus.getDone(null).getRed());
            assertNull(FlowEngine.jsonConvert, "core 单模块没有注册 JsonConvert SPI 实现");
        } finally {
            FlowEngine.jsonConvert = jsonConvert;
        }
    }

    @Test
    void idUtils_defaultGeneratorValidatesBoundsAndGeneratesPositiveIds() {
        assertTrue(IdUtils.nextId() > 0);
        assertThrows(IllegalArgumentException.class, () -> IdUtils.nextId(32, 0));
    }
}

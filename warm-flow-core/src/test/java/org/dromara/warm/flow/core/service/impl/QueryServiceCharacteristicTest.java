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
import org.dromara.warm.flow.core.dto.FlowCombine;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 节点、办理人和历史任务查询分支特征测试。
 *
 * @author warm
 */
class QueryServiceCharacteristicTest {

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
    void previousNodeList_withCombine_matchesDefinitionReloadPath() {
        var definition = TestFlows.parallelFlow("query-node");
        FlowCombine combine = FlowEngine.defService().getFlowCombine(definition);

        List<Node> reloaded = FlowEngine.nodeService().previousNodeList(definition.getId(), "joinP");
        List<Node> reused = FlowEngine.nodeService().previousNodeList("joinP", combine);

        assertEquals(reloaded.stream().map(Node::getNodeCode).collect(Collectors.toSet()),
            reused.stream().map(Node::getNodeCode).collect(Collectors.toSet()));
        assertTrue(reused.stream().map(Node::getNodeCode).collect(Collectors.toSet())
            .containsAll(Set.of("a1", "b1")));
    }

    @Test
    void userQueries_coverEmptySingleAndMultipleTypeBranches() {
        Task task = FlowEngine.newTask().setId(700L);
        User approval = FlowEngine.userService().structureUser(task.getId(), "zhangsan",
            org.dromara.warm.flow.core.enums.UserType.APPROVAL.getKey());
        User transfer = FlowEngine.userService().structureUser(task.getId(), "lisi",
            org.dromara.warm.flow.core.enums.UserType.TRANSFER.getKey());
        harness.userDao.save(approval);
        harness.userDao.save(transfer);

        assertEquals(2, FlowEngine.userService().listByAssociatedAndTypes(task.getId()).size());
        assertEquals(1, FlowEngine.userService().listByAssociatedAndTypes(task.getId(),
            org.dromara.warm.flow.core.enums.UserType.APPROVAL.getKey()).size());
        assertEquals(2, FlowEngine.userService().listByAssociatedAndTypes(task.getId(),
            org.dromara.warm.flow.core.enums.UserType.APPROVAL.getKey(),
            org.dromara.warm.flow.core.enums.UserType.TRANSFER.getKey()).size());
        assertEquals(List.of("lisi"), FlowEngine.userService().getPermission(task.getId(),
            org.dromara.warm.flow.core.enums.UserType.TRANSFER.getKey()));
        assertEquals(1, FlowEngine.userService().listByProcessedBys(task.getId(), "lisi").size());
        assertEquals(1, FlowEngine.userService().getByProcessedBys(task.getId(), List.of("zhangsan"),
            org.dromara.warm.flow.core.enums.UserType.APPROVAL.getKey()).size());
    }

    @Test
    void historyQueries_coverCooperateTypeBranchesAndDeleteByInstance() {
        HisTask pass = FlowEngine.newHisTask().setTaskId(800L).setInstanceId(900L)
            .setNodeCode("apply").setCooperateType(CooperateType.APPROVAL.getKey()).setSkipType(SkipType.PASS.getKey());
        HisTask sign = FlowEngine.newHisTask().setTaskId(800L).setInstanceId(900L)
            .setNodeCode("apply").setCooperateType(CooperateType.COUNTERSIGN.getKey()).setSkipType(SkipType.PASS.getKey());
        HisTask other = FlowEngine.newHisTask().setTaskId(801L).setInstanceId(901L)
            .setNodeCode("audit").setCooperateType(CooperateType.APPROVAL.getKey()).setSkipType(SkipType.REJECT.getKey());
        harness.hisTaskDao.save(pass);
        harness.hisTaskDao.save(sign);
        harness.hisTaskDao.save(other);

        assertEquals(2, FlowEngine.hisTaskService().listByTaskId(800L).size());
        assertEquals(2, FlowEngine.hisTaskService().listByTaskIdAndCooperateTypes(800L).size());
        assertEquals(1, FlowEngine.hisTaskService().listByTaskIdAndCooperateTypes(800L,
            CooperateType.COUNTERSIGN.getKey()).size());
        assertEquals(2, FlowEngine.hisTaskService().listByTaskIdAndCooperateTypes(800L,
            CooperateType.APPROVAL.getKey(), CooperateType.COUNTERSIGN.getKey()).size());
        assertEquals(2, FlowEngine.hisTaskService().getByInsAndNodeCodes(900L, List.of("apply")).size());
        assertEquals(2, FlowEngine.hisTaskService().getByInsId(900L).size());
        assertTrue(FlowEngine.hisTaskService().deleteByInsIds(List.of(900L)));
        assertEquals(1, harness.hisTaskDao.size());
    }
}

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
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.listener.GlobalListener;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.test.listener.MutatingGlobalListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 执行作用域快照语义特征测试：R2/R3/R5 接入后，监听器在 start 触发点中途写库
 * （新增待办/办理人/定义图分支）不进入本次操作视图——操作内已加载的快照固定复用。
 * 改造前（回调后重查）这些写入会被后续步骤拾取，本类锁定的是接入后的新语义。
 *
 * @author warm
 */
class ExecutionSnapshotCharacteristicTest {

    private FlowTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new FlowTestHarness();
        harness.register(GlobalListener.class, MutatingGlobalListener.instance());
        // harness 构造时已把默认全局监听器固化进 FlowEngine 字段，需重新解析才能生效
        FlowEngine.initGlobalListener(null);
    }

    @AfterEach
    void tearDown() {
        MutatingGlobalListener.onStart = null;
        harness.close();
    }

    @Test
    void revoke_cleanupUsesSnapshot_listenerAddedTaskSurvives() {
        Instance instance = TestFlows.start("snap1", "biz-s1");
        Task extra = FlowEngine.newTask().setId(900L)
                .setDefinitionId(instance.getDefinitionId())
                .setInstanceId(instance.getId())
                .setNodeCode("apply").setNodeName("apply")
                .setNodeType(NodeType.BETWEEN.getKey())
                .setFlowStatus(FlowStatus.APPROVAL.getKey());
        MutatingGlobalListener.onStart = variable -> harness.taskDao.save(extra);

        FlowEngine.taskService().revoke(instance.getId(), TestFlows.context(TestFlows.HANDLER));

        // R3 语义：撤回清理按监听器执行前的待办快照，中途写入的待办不被清理（旧语义为重查后一并清理）
        assertTrue(harness.daoLog.stream().anyMatch(l -> l.startsWith("TaskMemDao.save[")
                        && l.contains("Id=900") && l.contains("NodeCode=apply")),
                "前置：监听器应已中途写入额外待办: " + harness.daoLog);
        assertNotNull(harness.taskDao.raw(900L), "监听器中途新增的待办不应被撤回清理");
        assertEquals(2, harness.taskDao.size(), "原待办按快照清理后应仅剩新撤回待办与监听器新增待办");
    }

    @Test
    void cooperate_countingUsesSnapshot_listenerAddedApproverIgnored() {
        Instance instance = TestFlows.startCooperateFlow("snap2", "50"
                , TestFlows.HANDLER + "@@lisi", "biz-s2");
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        Task applyTask = TestFlows.currentTask(instance.getId());
        MutatingGlobalListener.onStart = variable -> {
            if (applyTask.getId().equals(variable.getTask().getId())) {
                harness.userDao.save(FlowEngine.userService().structureUser(applyTask.getId(), "wangwu"
                        , UserType.APPROVAL.getKey()));
            }
        };

        TestFlows.pass(applyTask.getId(), TestFlows.HANDLER);

        // R5 语义：会签计数按操作快照 2 人，首票 1/2 = 50% >= 50% 即流转
        // （旧语义为重查 3 人，1/3 = 33% 未达阈值暂存）
        assertTrue(harness.daoLog.stream().anyMatch(l -> l.startsWith("UserMemDao.save[")
                        && l.contains("ProcessedBy=wangwu")),
                "前置：监听器应已中途写入第三名审批人: " + harness.daoLog);
        assertEquals(0, harness.taskDao.size(), "快照口径下首票应达到通过率并流转");
        assertEquals(FlowStatus.FINISHED.getKey(), harness.insDao.raw(instance.getId()).getFlowStatus());
    }

    @Test
    void joinJudge_usesOperationGraph_listenerAddedBranchIgnored() {
        Instance instance = TestFlows.startParallelFlow("snap3", "biz-s3");
        TestFlows.pendingTasks(instance.getId()).stream()
                .filter(t -> "a1".equals(t.getNodeCode())).findFirst()
                .ifPresent(a1 -> TestFlows.pass(a1.getId(), TestFlows.HANDLER));
        Task b1 = TestFlows.currentTask(instance.getId());
        MutatingGlobalListener.onStart = variable -> {
            if ("b1".equals(variable.getNode().getNodeCode())) {
                // 中途插入新分支 x1 → joinP：节点 + 连线 + 活动待办
                FlowEngine.nodeService().save(FlowEngine.newNode()
                        .setDefinitionId(instance.getDefinitionId())
                        .setNodeCode("x1").setNodeName("x1")
                        .setNodeType(NodeType.BETWEEN.getKey()));
                FlowEngine.skipService().save(FlowEngine.newSkip()
                        .setDefinitionId(instance.getDefinitionId())
                        .setNowNodeCode("x1").setNowNodeType(NodeType.BETWEEN.getKey())
                        .setNextNodeCode("joinP").setNextNodeType(NodeType.PARALLEL.getKey())
                        .setSkipName("x1_joinP").setSkipType(SkipType.PASS.getKey()));
                harness.taskDao.save(FlowEngine.newTask().setId(901L)
                        .setDefinitionId(instance.getDefinitionId())
                        .setInstanceId(instance.getId())
                        .setNodeCode("x1").setNodeName("x1")
                        .setNodeType(NodeType.BETWEEN.getKey())
                        .setFlowStatus(FlowStatus.APPROVAL.getKey()));
            }
        };

        TestFlows.pass(b1.getId(), TestFlows.HANDLER);

        // R2 语义：汇聚前置判定用操作内定义图（前置 {a1,b1}，活动待办 {b1} = 1）→ 放行完成
        // （旧语义为回调后重查图含 x1，活动待办 2 > 1 → 汇聚等待）
        assertTrue(harness.daoLog.stream().anyMatch(l -> l.startsWith("NodeMemDao.save[")
                        && l.contains("NodeCode=x1")),
                "前置：监听器应已中途写入新分支节点: " + harness.daoLog);
        Instance persisted = harness.insDao.raw(instance.getId());
        assertEquals("end", persisted.getNodeCode());
        assertEquals(FlowStatus.FINISHED.getKey(), persisted.getFlowStatus());
        assertEquals(0, harness.taskDao.size(), "x1 待办应由收尾 handUndoneTask 清理");
    }
}

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
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * guard 表矩阵特征测试：逐格锁定 FlowOp × 状态谓词 → 异常常量。
 * 办理族五行（EXECUTE/TERMINATE/UPDATE_HANDLERS/LOAD/REVOKE）共享全谓词行；
 * DELETE 为唯一离群行——仅要求激活，终态/已结束实例放行（清理已完成流程）。
 *
 * @author warm
 */
class FlowStatusMachineCharacteristicTest {

    private static final FlowOp[] OPERABLE_OPS = {
        FlowOp.EXECUTE, FlowOp.TERMINATE, FlowOp.UPDATE_HANDLERS, FlowOp.LOAD, FlowOp.REVOKE};

    private static final FlowStatus[] TERMINAL_STATUSES = {
        FlowStatus.FINISHED, FlowStatus.TERMINATE, FlowStatus.NULLIFY, FlowStatus.CANCEL, FlowStatus.INVALID};

    private FlowTestHarness harness;

    @BeforeEach
    void setUp() {
        // 仅用于引导 FlowEngine 实体 supplier，guard 校验本身纯内存、无 DAO 访问
        harness = new FlowTestHarness();
    }

    @AfterEach
    void tearDown() {
        harness.close();
    }

    @Test
    void matrix_operableRow_eachGuardViolated() {
        for (FlowOp op : OPERABLE_OPS) {
            // 定义挂起 / 实例挂起 → NOT_ACTIVITY
            assertGuard(op, suspendedDef(), runningIns(), ExceptionCons.NOT_ACTIVITY);
            assertGuard(op, activeDef(), runningIns().setActivityStatus(ActivityStatus.SUSPENDED.getKey())
                , ExceptionCons.NOT_ACTIVITY);
            // 五个终态逐一 → FLOW_FINISH
            for (FlowStatus status : TERMINAL_STATUSES) {
                assertGuard(op, activeDef(), runningIns().setFlowStatus(status.getKey())
                    , ExceptionCons.FLOW_FINISH);
            }
            // 结束节点 → FLOW_FINISH
            assertGuard(op, activeDef(), runningIns().setNodeType(NodeType.END.getKey())
                , ExceptionCons.FLOW_FINISH);
            // 正向格：激活 ∧ 流转中 ∧ 中间节点 → 放行
            assertDoesNotThrow(() -> FlowStatusMachine.checkGuards(op, activeDef(), runningIns()));
        }
    }

    @Test
    void matrix_deleteRow_onlyRequiresActivity() {
        // 挂起 → NOT_ACTIVITY（删除行唯一 guard）
        assertGuard(FlowOp.DELETE, suspendedDef(), runningIns(), ExceptionCons.NOT_ACTIVITY);
        assertGuard(FlowOp.DELETE, activeDef(), runningIns().setActivityStatus(ActivityStatus.SUSPENDED.getKey())
            , ExceptionCons.NOT_ACTIVITY);
        // 终态 → 放行；结束节点 → 放行；终态+结束 → 放行（离群行：终态可删）
        assertDoesNotThrow(() -> FlowStatusMachine.checkGuards(FlowOp.DELETE, activeDef()
            , runningIns().setFlowStatus(FlowStatus.FINISHED.getKey())));
        assertDoesNotThrow(() -> FlowStatusMachine.checkGuards(FlowOp.DELETE, activeDef()
            , runningIns().setNodeType(NodeType.END.getKey())));
        assertDoesNotThrow(() -> FlowStatusMachine.checkGuards(FlowOp.DELETE, activeDef()
            , runningIns().setFlowStatus(FlowStatus.FINISHED.getKey()).setNodeType(NodeType.END.getKey())));
    }

    @Test
    void matrix_tableCoversEveryOp() {
        // 缺行会以 NPE 抛出；正向格全部不抛即表覆盖完整
        for (FlowOp op : FlowOp.values()) {
            assertDoesNotThrow(() -> FlowStatusMachine.checkGuards(op, activeDef(), runningIns()));
        }
    }

    @Test
    void guardOrder_activityPrecedesTerminal() {
        // 挂起 + 终态叠加 → NOT_ACTIVITY（非 FLOW_FINISH）：激活断言先于终态断言。
        // 终态与结束节点共用 FLOW_FINISH 文案，先后经消息不可区分，由 StateGuard 声明序结构锁定。
        assertGuard(FlowOp.EXECUTE, suspendedDef()
            , runningIns().setFlowStatus(FlowStatus.FINISHED.getKey()), ExceptionCons.NOT_ACTIVITY);
    }

    private static void assertGuard(FlowOp op, Definition definition, Instance instance, String expectedMessage) {
        FlowException ex = assertThrows(FlowException.class,
                () -> FlowStatusMachine.checkGuards(op, definition, instance));
        assertEquals(expectedMessage, ex.getMessage());
    }

    private static Definition activeDef() {
        return FlowEngine.newDef().setActivityStatus(ActivityStatus.ACTIVITY.getKey());
    }

    private static Definition suspendedDef() {
        return FlowEngine.newDef().setActivityStatus(ActivityStatus.SUSPENDED.getKey());
    }

    private static Instance runningIns() {
        return FlowEngine.newIns()
                .setActivityStatus(ActivityStatus.ACTIVITY.getKey())
                .setFlowStatus(FlowStatus.APPROVAL.getKey())
                .setNodeType(NodeType.BETWEEN.getKey());
    }
}

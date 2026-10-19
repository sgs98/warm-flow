package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.TaskStatus;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.spi.StatusMapper;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 状态映射扩展点特征测试：默认映射保持内置编码；宿主映射分别接管实例、任务、协作三个维度。
 *
 * @author may
 */
class StatusMapperCharacteristicTest {

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
    void defaultMapper_keepsBuiltInCodes() {
        Instance ins = TestFlows.start("mapperDefault", "BIZ-1");
        // 实例：待提交；待办：待办理
        assertEquals(FlowStatus.DRAFT.getKey(), ins.getFlowStatus());
        assertEquals(TaskStatus.PENDING.getKey(), TestFlows.currentTask(ins.getId()).getFlowStatus());

        Instance finished = TestFlows.pass(TestFlows.currentTask(ins.getId()).getId(), TestFlows.HANDLER);
        // 实例收口为已完成；历史任务为通过
        assertEquals(FlowStatus.FINISHED.getKey(), finished.getFlowStatus());
        assertEquals(TaskStatus.PASS.getKey(), lastHis(ins.getId()).getFlowStatus());
        assertTrue(FlowStatus.isTerminalKey(FlowStatus.FINISHED.getKey()));
        assertEquals("已完成", FlowEngine.statusMapper().label(FlowStatus.FINISHED.getKey()));
        assertEquals("通过", FlowEngine.statusMapper().taskLabel(TaskStatus.PASS.getKey()));
        // 两个维度独立编码：实例「终止」与任务「通过」不再是同一个数值
        assertEquals("终止", FlowEngine.statusMapper().label(FlowStatus.TERMINATE.getKey(), true));
        assertEquals("通过", FlowEngine.statusMapper().label(TaskStatus.PASS.getKey(), false));
    }

    @Test
    void customMapper_takesOverEachDimension() {
        FlowEngine.setStatusMapper(new BizStatusMapper());

        Instance ins = TestFlows.start("mapperCustom", "BIZ-2");
        assertEquals("draft", ins.getFlowStatus());
        assertEquals("waiting", TestFlows.currentTask(ins.getId()).getFlowStatus());

        Instance finished = TestFlows.pass(TestFlows.currentTask(ins.getId()).getId(), TestFlows.HANDLER);
        assertEquals("finish", finished.getFlowStatus());
        assertEquals("pass", lastHis(ins.getId()).getFlowStatus());
    }

    @Test
    void customMapper_terminalGuardFollowsHostCodes() {
        FlowEngine.setStatusMapper(new BizStatusMapper());

        Definition def = FlowEngine.newDef().setActivityStatus(ActivityStatus.ACTIVITY.getKey());
        Instance running = runningIns().setFlowStatus("waiting");
        assertDoesNotThrow(() -> FlowStatusMachine.checkGuards(FlowOp.EXECUTE, def, running));

        FlowException ex = assertThrows(FlowException.class,
            () -> FlowStatusMachine.checkGuards(FlowOp.EXECUTE, def, runningIns().setFlowStatus("finish")));
        assertEquals(ExceptionCons.FLOW_FINISH, ex.getMessage());
    }

    @Test
    void cooperateStatus_mapsByAction() {
        FlowEngine.setStatusMapper(new BizStatusMapper());

        Instance ins = TestFlows.start("coopCustom", "BIZ-4");
        Long taskId = TestFlows.currentTask(ins.getId()).getId();
        FlowEngine.taskService().updateHandlers(taskId, TestFlows.context(TestFlows.HANDLER)
            , List.of("lisi"), List.of(TestFlows.HANDLER), CooperateType.TRANSFER.getKey());

        HisTask his = lastHis(ins.getId());
        assertEquals(CooperateType.TRANSFER.getKey(), his.getCooperateType());
        assertEquals("transfer", his.getFlowStatus());
    }

    @Test
    void cooperateStatus_hasBuiltInDefaults() {
        // 内置默认：协作后任务仍等待办理，统一回落到「待审批」
        StatusMapper defaults = new StatusMapper() {
        };
        assertEquals(TaskStatus.PENDING.getKey(), defaults.cooperateStatus(CooperateType.TRANSFER));
        assertEquals(TaskStatus.PENDING.getKey(), defaults.cooperateStatus(CooperateType.ADD_SIGNER));
        assertEquals(TaskStatus.PENDING.getKey(), defaults.cooperateStatus(CooperateType.NONE));

        // 宿主只覆写关心的动作，未覆写的回落到内置默认（默认再经宿主的 taskStatus 映射）
        StatusMapper custom = new BizStatusMapper();
        assertEquals("transfer", custom.cooperateStatus(CooperateType.TRANSFER));
        assertEquals("waiting", custom.cooperateStatus(CooperateType.NONE));
    }

    @Test
    void manualStatusWins() {
        FlowEngine.setStatusMapper(new BizStatusMapper());

        Instance ins = TestFlows.start("coopManual", "BIZ-5");
        Long taskId = TestFlows.currentTask(ins.getId()).getId();
        WorkflowContext context = TestFlows.context(TestFlows.HANDLER);
        context.setTaskStatus("biz_custom");
        FlowEngine.taskService().updateHandlers(taskId, context
            , List.of("lisi"), List.of(TestFlows.HANDLER), CooperateType.TRANSFER.getKey());

        assertEquals("biz_custom", lastHis(ins.getId()).getFlowStatus());
    }

    @Test
    void customTerminalCodes_areHonoured() {
        StatusMapper mapper = new BizStatusMapper() {
            @Override
            public boolean isTerminal(String code) {
                return super.isTerminal(code) || "archived".equals(code);
            }
        };
        FlowEngine.setStatusMapper(mapper);

        assertTrue(mapper.isTerminal("archived"));
        assertTrue(mapper.isTerminal("finish"));
        assertFalse(mapper.isTerminal("waiting"));
        assertEquals("已完成", mapper.label("finish"));
        assertEquals("unknown", mapper.label("unknown"));
    }

    private static HisTask lastHis(Long instanceId) {
        List<HisTask> list = FlowEngine.hisTaskService().list(FlowEngine.newHisTask().setInstanceId(instanceId));
        return list.get(list.size() - 1);
    }

    private static Instance runningIns() {
        return FlowEngine.newIns()
            .setActivityStatus(ActivityStatus.ACTIVITY.getKey())
            .setFlowStatus(FlowStatus.WAITING.getKey())
            .setNodeType(NodeType.BETWEEN.getKey());
    }

    /**
     * 宿主状态映射示例：三个维度分别映射到业务编码，并声明哪些实例编码是终态。
     */
    private static class BizStatusMapper implements StatusMapper {

        @Override
        public String instanceStatus(FlowStatus semantic) {
            return switch (semantic) {
                case DRAFT -> "draft";
                case WAITING -> "waiting";
                case FINISHED -> "finish";
                case BACK -> "back";
                case TERMINATE -> "termination";
                case CANCEL -> "cancel";
                case INVALID -> "invalid";
                case RESTART -> "restart";
            };
        }

        @Override
        public String taskStatus(TaskStatus semantic) {
            return switch (semantic) {
                case PENDING -> "waiting";
                case PASS -> "pass";
                case BACK -> "back";
                case INVALID -> "invalid";
                case TAKE_BACK -> "take_back";
                case AUTO_PASS -> "auto_pass";
                case COPY -> "copy";
            };
        }

        @Override
        public String cooperateStatus(CooperateType type) {
            return switch (type) {
                case TRANSFER -> "transfer";
                case DELEGATE -> "delegate";
                case ADD_SIGNER -> "biz_sign";
                case REMOVE_SIGNER -> "biz_unsign";
                default -> StatusMapper.super.cooperateStatus(type);
            };
        }

        @Override
        public boolean isTerminal(String code) {
            return Set.of("finish", "termination", "cancel", "invalid").contains(code);
        }

        @Override
        public String label(String code) {
            return switch (code) {
                case "draft" -> "草稿";
                case "waiting" -> "待审核";
                case "finish" -> "已完成";
                case "termination" -> "已终止";
                case "cancel" -> "已撤销";
                case "invalid" -> "已作废";
                case "back" -> "已退回";
                default -> code;
            };
        }
    }
}

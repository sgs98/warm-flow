package org.dromara.warm.flow.core.workflow;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.config.WarmFlow;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.spi.PermissionHandler;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.workflow.command.CompleteCommand;
import org.dromara.warm.flow.core.workflow.command.StartCommand;
import org.dromara.warm.flow.core.workflow.context.OperatorContext;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 门面上下文对「跳过权限」的处理特征测试：{@code ignore} 时完全不接触 PermissionHandler，
 * {@code ignorePermission} 只跳过权限集合的拉取但仍补操作人身份，默认情况下两者都取。
 *
 * @author may
 */
class WorkflowContextPermissionSkipCharacteristicTest {

    private FlowTestHarness harness;
    private CountingPermissionHandler permissionHandler;

    @BeforeEach
    void setUp() {
        harness = new FlowTestHarness();
        permissionHandler = new CountingPermissionHandler();
        harness.register(PermissionHandler.class, permissionHandler);
        FlowEngine.initPermissionHandler(null);
    }

    @AfterEach
    void tearDown() {
        harness.close();
        // 清掉测试注入的处理器，避免影响其它测试类
        FlowEngine.initPermissionHandler(null);
    }

    @Test
    void ignoreSkipsPermissionHandlerEntirely() {
        OperatorContext operator = new OperatorContext();
        operator.setHandler("operator");
        operator.setIgnore(true);

        WorkflowContext context = WorkflowContextMapper.toContext(command(operator));

        assertEquals("operator", context.getHandler());
        assertTrue(context.isIgnore());
        assertEquals(0, permissionHandler.handlerCalls, "ignore 时不应访问 PermissionHandler#getHandler");
        assertEquals(0, permissionHandler.permissionCalls, "ignore 时不应访问 PermissionHandler#permissions");
        assertNull(context.getPermissions());
    }

    @Test
    void ignoreWithoutHandlerFallsBackToSystemHandler() {
        OperatorContext operator = new OperatorContext();
        operator.setIgnore(true);

        WorkflowContext context = WorkflowContextMapper.toContext(command(operator));

        // 系统调用（任务补偿 / 定时推进）必须落一个操作人身份，否则历史 approver / createBy 为空
        assertEquals("system", context.getHandler(), "ignore 且未传 handler 时应回落 systemHandler");
        assertEquals(0, permissionHandler.handlerCalls, "ignore 时仍不应访问 PermissionHandler");
        assertEquals(0, permissionHandler.permissionCalls);
    }

    @Test
    void ignorePermissionKeepsHandlerButSkipsPermissionQuery() {
        OperatorContext operator = new OperatorContext();
        operator.setIgnorePermission(true);

        WorkflowContext context = WorkflowContextMapper.toContext(command(operator));

        assertEquals("login-user", context.getHandler(), "忽略权限仍要补操作人身份，否则历史留痕缺办理人");
        assertEquals(1, permissionHandler.handlerCalls);
        assertEquals(0, permissionHandler.permissionCalls, "忽略权限时不应拉取权限集合");
        assertNull(context.getPermissions());
    }

    @Test
    void defaultContextQueriesHandlerAndPermissions() {
        WorkflowContext context = WorkflowContextMapper.toContext(command(new OperatorContext()));

        assertEquals("login-user", context.getHandler());
        assertEquals(List.of("perm:a"), context.getPermissions());
        assertEquals(1, permissionHandler.handlerCalls);
        assertEquals(1, permissionHandler.permissionCalls);
    }

    @Test
    void ignorePermissionWithoutLoginUserFallsBackToSystemHandler() {
        permissionHandler = new CountingPermissionHandler((String) null);
        harness.register(PermissionHandler.class, permissionHandler);
        FlowEngine.initPermissionHandler(null);
        OperatorContext operator = new OperatorContext();
        operator.setIgnorePermission(true);

        WorkflowContext context = WorkflowContextMapper.toContext(command(operator));

        assertEquals("system", context.getHandler(), "宿主取不到登录人时应回落 systemHandler，不能留空");
        assertEquals(1, permissionHandler.handlerCalls);
        assertEquals(0, permissionHandler.permissionCalls);
    }

    @Test
    void explicitHandlerWinsOverPermissionHandler() {
        OperatorContext operator = new OperatorContext();
        operator.setHandler("operator");

        WorkflowContext context = WorkflowContextMapper.toContext(command(operator));

        assertEquals("operator", context.getHandler());
        assertEquals(0, permissionHandler.handlerCalls, "调用方显式传了操作人，不应再取登录用户");
        assertEquals(1, permissionHandler.permissionCalls);
    }

    @Test
    void ignorePermissionSwallowsLoginExceptionAndFallsBack() {
        IllegalStateException notLogin = new IllegalStateException("未登录");
        permissionHandler = new CountingPermissionHandler(notLogin);
        harness.register(PermissionHandler.class, permissionHandler);
        FlowEngine.initPermissionHandler(null);
        OperatorContext operator = new OperatorContext();
        operator.setIgnorePermission(true);

        WorkflowContext context = WorkflowContextMapper.toContext(command(operator));

        assertEquals("system", context.getHandler(), "跳过权限时宿主抛未登录异常也不应阻断，回落 systemHandler");
        assertEquals(1, permissionHandler.handlerCalls);
        assertEquals(0, permissionHandler.permissionCalls);
    }

    @Test
    void normalCallKeepsLoginException() {
        IllegalStateException notLogin = new IllegalStateException("未登录");
        permissionHandler = new CountingPermissionHandler(notLogin);
        harness.register(PermissionHandler.class, permissionHandler);
        FlowEngine.initPermissionHandler(null);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
            () -> WorkflowContextMapper.toContext(command(new OperatorContext())));

        // 正常办理不吞鉴权异常，保持改造前的可见性
        assertEquals("未登录", thrown.getMessage());
    }

    @Test
    void systemCompleteWithoutHandlerWritesSystemApprover() {
        Instance instance = TestFlows.start("sysComplete", "biz-sys-complete");
        Task task = TestFlows.currentTask(instance.getId());
        CompleteCommand command = new CompleteCommand();
        command.setTaskId(task.getId());
        OperatorContext operator = new OperatorContext();
        operator.setIgnore(true);
        command.setOperator(operator);

        FlowEngine.workflow().complete(command);

        HisTask done = harness.hisTaskDao.all().stream()
            .filter(hisTask -> task.getId().equals(hisTask.getTaskId()))
            .findFirst().orElseThrow();
        // 补偿 / 系统办理这类无登录态调用：审批人必须落库，不能留空
        assertEquals("system", done.getApprover(), "系统办理的历史审批人应为 systemHandler");
    }

    @Test
    void createWithoutConfigFallsBackToSystem() {
        WarmFlow original = FlowEngine.getFlowConfig();
        try {
            FlowEngine.setFlowConfig(null);

            assertEquals("system", WorkflowContext.create().getHandler(),
                "引擎未初始化时 create() 也不应抛 NPE");
            assertEquals("system", WorkflowContext.create(null).getHandler());
        } finally {
            FlowEngine.setFlowConfig(original);
        }
    }

    private StartCommand command(OperatorContext operator) {
        StartCommand command = new StartCommand();
        command.setOperator(operator);
        return command;
    }

    /**
     * 计数用的权限处理器：只关心 getHandler / permissions 被调用的次数。
     */
    private static final class CountingPermissionHandler implements PermissionHandler {

        /**
         * getHandler 返回值，{@code null} 表示宿主取不到登录人。
         */
        private final String returnedHandler;

        /**
         * getHandler 需要抛出的异常，{@code null} 表示正常返回。
         */
        private final RuntimeException handlerException;

        /**
         * getHandler 调用次数。
         */
        private int handlerCalls;

        /**
         * permissions 调用次数。
         */
        private int permissionCalls;

        private CountingPermissionHandler() {
            this("login-user");
        }

        private CountingPermissionHandler(String returnedHandler) {
            this.returnedHandler = returnedHandler;
            this.handlerException = null;
        }

        private CountingPermissionHandler(RuntimeException handlerException) {
            this.returnedHandler = null;
            this.handlerException = handlerException;
        }

        @Override
        public List<String> permissions() {
            permissionCalls++;
            return List.of("perm:a");
        }

        @Override
        public String getHandler() {
            handlerCalls++;
            if (handlerException != null) {
                throw handlerException;
            }
            return returnedHandler;
        }
    }
}

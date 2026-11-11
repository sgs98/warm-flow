package org.dromara.warm.flow.spring.boot.config;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.config.WarmFlow;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.entity.*;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.invoker.FrameInvoker;
import org.dromara.warm.flow.core.service.*;
import org.dromara.warm.flow.core.service.impl.*;
import org.dromara.warm.flow.core.workflow.WorkflowService;
import org.dromara.warm.flow.core.workflow.command.*;
import org.dromara.warm.flow.core.workflow.context.OperatorContext;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.dromara.warm.flow.orm.dao.*;
import org.dromara.warm.flow.core.orm.dao.*;
import org.dromara.warm.flow.orm.entity.*;
import org.dromara.warm.plugin.json.JsonConvertGson;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** 真实 Mapper + H2 + Spring 事务验证，不用内存 DAO 推断原子性。 @author may */
class RejectReturnTransactionTest {
    private static final String DIRECT = "{\"reject\":{\"schemaVersion\":1,\"afterApproval\":\"RETURN_TO_REJECTOR\"}}";
    private final Map<Class<?>, Object> beans = new HashMap<>();
    private JdbcTemplate jdbc;
    private TransactionTemplate template;
    private TransactionalTaskService tasks;
    private TransactionalInsService instances;
    private final AtomicReference<String> failStage = new AtomicReference<>();

    @SuppressWarnings({"rawtypes", "unchecked"})
    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource ds = new UnpooledDataSource("org.h2.Driver",
            "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        jdbc = new JdbcTemplate(ds);
        template = new TransactionTemplate(new DataSourceTransactionManager(ds));
        Class<?>[] entities = {FlowDefinition.class, FlowNode.class, FlowSkip.class, FlowInstance.class,
            FlowTask.class, FlowHisTask.class, FlowUser.class, FlowForm.class, FlowEventWait.class};
        String[] names = {"FlowDefinition", "FlowNode", "FlowSkip", "FlowInstance", "FlowTask",
            "FlowHisTask", "FlowUser", "FlowForm", "FlowEventWait"};
        for (int i = 0; i < entities.length; i++) { createTable(entities[i], snake(names[i])); }
        com.baomidou.mybatisplus.core.MybatisConfiguration cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        cfg.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
        cfg.setEnvironment(new Environment("test", new SpringManagedTransactionFactory(), ds));
        for (String name : names) {
            cfg.addMapper(Class.forName("org.dromara.warm.flow.orm.mapper." + name + "Mapper"));
        }
        SqlSessionTemplate sessions = new SqlSessionTemplate(new com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder().build(cfg));
        for (String name : names) {
            Class<?> mapper = Class.forName("org.dromara.warm.flow.orm.mapper." + name + "Mapper");
            beans.put(mapper, sessions.getMapper(mapper));
        }
        FrameInvoker.setBeanFunction(beans::get);
        FlowEngine.setNewDef(FlowDefinition::new); FlowEngine.setNewNode(FlowNode::new);
        FlowEngine.setNewSkip(FlowSkip::new); FlowEngine.setNewIns(FlowInstance::new);
        FlowEngine.setNewTask(FlowTask::new); FlowEngine.setNewHisTask(FlowHisTask::new);
        FlowEngine.setNewUser(FlowUser::new); FlowEngine.setNewForm(FlowForm::new);
        FlowEngine.setNewEventWait(FlowEventWait::new);
        FlowEngine.jsonConvert = new JsonConvertGson();
        WarmFlow config = new WarmFlow();
        config.setDataSourceType("mysql");
        FlowEngine.setFlowConfig(config);
        FlowEngine.initDataFillHandler(null); FlowEngine.initTenantHandler(null);
        FlowEngine.initPermissionHandler(null); FlowEngine.initGlobalListener(); FlowEngine.setStatusMapper(null);
        beans.put(DefService.class, new DefServiceImpl().setDao((FlowDefinitionDao) new FlowDefinitionDaoImpl()));
        beans.put(NodeService.class, new NodeServiceImpl().setDao((FlowNodeDao) new FlowNodeDaoImpl()));
        beans.put(SkipService.class, new SkipServiceImpl().setDao((FlowSkipDao) new FlowSkipDaoImpl()));
        instances = new TransactionalInsService(template);
        FlowInstanceDaoImpl instanceDao = new FlowInstanceDaoImpl() {
            @Override public int updateById(FlowInstance value) {
                int result = super.updateById(value);
                failIf("instance");
                return result;
            }
        };
        instances.setDao((FlowInstanceDao) instanceDao); beans.put(InsService.class, instances);
        tasks = new TransactionalTaskService(template);
        FlowTaskDaoImpl taskDao = new FlowTaskDaoImpl() {
            @Override public void saveBatch(List<FlowTask> values) {
                super.saveBatch(values); failIf("task");
            }
        };
        tasks.setDao((FlowTaskDao) taskDao); beans.put(TaskService.class, tasks);
        FlowHisTaskDaoImpl historyDao = new FlowHisTaskDaoImpl() {
            @Override public int save(FlowHisTask value) {
                int result = super.save(value); failIf("history"); return result;
            }
        };
        beans.put(HisTaskService.class, new HisTaskServiceImpl().setDao((FlowHisTaskDao) historyDao));
        FlowUserDaoImpl users = new FlowUserDaoImpl() {
            @Override public void saveBatch(List<FlowUser> values) {
                super.saveBatch(values);
                failIf("user");
            }
        };
        beans.put(UserService.class, new UserServiceImpl().setDao((FlowUserDao) users));
        beans.put(FormService.class, new FormServiceImpl().setDao((FlowFormDao) new FlowFormDaoImpl()));
        beans.put(EventWaitService.class, new EventWaitServiceImpl().setDao((FlowEventWaitDao) new FlowEventWaitDaoImpl()));
        beans.put(ChartService.class, new ChartServiceImpl());
        beans.put(WorkflowService.class, new TransactionalWorkflowService(template));
    }

    @AfterEach
    void close() { FrameInvoker.setBeanFunction(type -> null); }

    @Test
    void noTransactionRejectsDirectModeButAllowsReplay() {
        Instance direct = start(DIRECT);
        TransactionalTaskService plain = new TransactionalTaskService(null);
        plain.setDao((FlowTaskDao) new FlowTaskDaoImpl());
        assertThrows(FlowException.class, () -> plain.execute(current(direct).getId(), context(), SkipType.PASS.getKey()));
        Instance replay = start(null);
        assertEquals("B", plain.execute(current(replay).getId(), context(), SkipType.PASS.getKey()).getNodeCode());
    }

    @Test
    void noTransactionRejectsStartBeforeInstanceCreation() {
        Definition definition = definition(DIRECT);
        TransactionalInsService plain = new TransactionalInsService(null);
        plain.setDao((FlowInstanceDao) new FlowInstanceDaoImpl());
        assertThrows(FlowException.class, () -> plain.start("biz", definition.getFlowCode(), context()));
        assertEquals(0, count("flow_instance"));
        assertEquals(0, count("flow_task"));
    }

    @Test
    void executeLocksBeforeDecisionRead() throws Exception {
        Instance instance = rejected();
        Long a = current(instance).getId();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> template.execute(status -> {
                InstanceRowLock.lock(instance.getId());
                entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new RuntimeException(e); }
                return tasks.execute(a, context(), SkipType.PASS.getKey());
            }));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            Future<?> second = pool.submit(() -> tasks.execute(a, context(), SkipType.PASS.getKey()));
            assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            assertThrows(ExecutionException.class, () -> second.get(5, TimeUnit.SECONDS));
            assertEquals("D", current(instance).getNodeCode());
            assertEquals(1, FlowEngine.taskService().getByInsId(instance.getId()).size());
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test
    void ambientRepeatableReadRejectsDirectWritesBeforeChanges() {
        Instance instance = rejected();
        Long a = current(instance).getId();
        String before = FlowEngine.insService().getById(instance.getId()).getDefJson();
        TransactionTemplate outer = new TransactionTemplate(template.getTransactionManager());
        outer.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        for (Runnable action : List.<Runnable>of(
            () -> tasks.execute(a, context(), SkipType.PASS.getKey()),
            () -> instances.removeVariables(instance.getId(), "v"),
            () -> tasks.terminateByInstanceId(instance.getId(), context()))) {
            FlowException error = assertThrows(FlowException.class, () -> outer.execute(status -> {
                FlowEngine.insService().getById(instance.getId()); // 建立外层旧读视图
                action.run(); return null;
            }));
            assertTrue(error.getMessage().contains("READ_COMMITTED"));
            assertEquals(a, current(instance).getId());
            assertEquals(before, FlowEngine.insService().getById(instance.getId()).getDefJson());
        }
    }

    @Test
    void ambientDefaultRepeatableReadIsAlsoRejected() {
        Instance instance = rejected();
        Long a = current(instance).getId();
        ((UnpooledDataSource) ((DataSourceTransactionManager) template.getTransactionManager()).getDataSource())
            .setDefaultTransactionIsolationLevel(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
        TransactionTemplate outer = new TransactionTemplate(template.getTransactionManager());
        FlowException error = assertThrows(FlowException.class, () -> outer.execute(status ->
            tasks.execute(a, context(), SkipType.PASS.getKey())));
        assertTrue(error.getMessage().contains("READ_COMMITTED"));
        assertEquals(a, current(instance).getId());
    }

    @Test
    void ownTransactionUsesReadCommittedWithoutChangingHostTemplate() {
        Instance instance = start(DIRECT);
        ((UnpooledDataSource) ((DataSourceTransactionManager) template.getTransactionManager()).getDataSource())
            .setDefaultTransactionIsolationLevel(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
        Integer isolation = new InstanceTransactionSupport(template).execute(instance.getId(), () ->
            jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Integer>) java.sql.Connection::getTransactionIsolation));
        assertEquals(java.sql.Connection.TRANSACTION_READ_COMMITTED, isolation.intValue());
        assertEquals(org.springframework.transaction.TransactionDefinition.ISOLATION_DEFAULT, template.getIsolationLevel());
    }

    @Test
    void customTemplateCannotDisableRequiredWriteTransaction() {
        Instance instance = start(DIRECT);
        TransactionTemplate host = new TransactionTemplate(template.getTransactionManager());
        host.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_SUPPORTS);
        assertTrue(new InstanceTransactionSupport(host).execute(instance.getId(),
            org.springframework.transaction.support.TransactionSynchronizationManager::isActualTransactionActive));
        assertEquals(org.springframework.transaction.TransactionDefinition.PROPAGATION_SUPPORTS, host.getPropagationBehavior());
    }

    @Test
    void changedRowCountZeroDoesNotMeanTheVisibleRowWasNotLocked() {
        Instance instance = rejected();
        Class<?> type = org.dromara.warm.flow.orm.mapper.FlowInstanceMapper.class;
        Object actual = beans.get(type);
        beans.put(type, java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (proxy, method, args) -> {
                Object result;
                try { result = method.invoke(actual, args); }
                catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                // 真实 SQL 先执行，只模拟驱动报告 changed rows 而不是 matched rows 的计数。
                boolean lock = method.getName().equals("lockInstance");
                if (method.getName().equals("update") && args != null && args.length == 2 && args[1] != null) {
                    lock = String.valueOf(args[1].getClass().getMethod("getSqlSet").invoke(args[1]))
                        .contains("update_time = update_time");
                }
                return lock ? 0 : result;
            }));
        try {
            tasks.execute(current(instance).getId(), context(), SkipType.PASS.getKey());
            assertEquals("D", current(instance).getNodeCode());
        } finally { beans.put(type, actual); }
    }

    @Test
    void readCommittedParticipationKeepsBusinessRollbackAtomic() {
        Instance instance = rejected();
        Long a = current(instance).getId();
        String before = FlowEngine.insService().getById(instance.getId()).getDefJson();
        TransactionTemplate outer = new TransactionTemplate(template.getTransactionManager());
        outer.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        outer.execute(status -> {
            tasks.execute(a, context(), SkipType.PASS.getKey());
            assertEquals("D", current(instance).getNodeCode());
            status.setRollbackOnly(); return null;
        });
        assertEquals(a, current(instance).getId());
        assertEquals(before, FlowEngine.insService().getById(instance.getId()).getDefJson());
    }

    @Test
    void staleAmbientReadViewCannotCompleteAgainOrResurrectSnapshot() throws Exception {
        Instance instance = rejected();
        Long a = current(instance).getId();
        CountDownLatch readView = new CountDownLatch(1), committed = new CountDownLatch(1);
        TransactionTemplate outer = new TransactionTemplate(template.getTransactionManager());
        outer.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<FlowException> stale = pool.submit(() -> assertThrows(FlowException.class, () -> outer.execute(status -> {
                assertNotNull(FlowEngine.taskService().getById(a));
                String old = FlowEngine.insService().getById(instance.getId()).getDefJson();
                readView.countDown();
                try { assertTrue(committed.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new AssertionError(e); }
                assertEquals(old, FlowEngine.insService().getById(instance.getId()).getDefJson());
                return tasks.execute(a, context(), SkipType.PASS.getKey());
            })));
            assertTrue(readView.await(2, TimeUnit.SECONDS));
            tasks.execute(a, context(), SkipType.PASS.getKey());
            committed.countDown();
            assertTrue(stale.get(5, TimeUnit.SECONDS).getMessage().contains("READ_COMMITTED"));
            assertEquals("D", current(instance).getNodeCode());
            assertEquals(1, FlowEngine.taskService().getByInsId(instance.getId()).size());
            assertFalse(FlowEngine.insService().getById(instance.getId()).getDefJson().contains("__rejectReturnRuntime"));
        } finally { committed.countDown(); pool.shutdownNow(); }
    }

    @Test
    void repeatableReadRejectsDirectStartButKeepsLegacyReplay() {
        Definition direct = definition(DIRECT);
        TransactionTemplate outer = new TransactionTemplate(template.getTransactionManager());
        outer.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        FlowException error = assertThrows(FlowException.class, () -> outer.execute(status ->
            instances.start("biz", direct.getFlowCode(), context())));
        assertTrue(error.getMessage().contains("READ_COMMITTED"));
        assertEquals(0, count("flow_instance"));
        Instance replay = start(null);
        outer.execute(status -> tasks.execute(current(replay).getId(), context(), SkipType.PASS.getKey()));
        assertEquals("B", current(replay).getNodeCode());
    }

    @Test
    void invisibleTenantAndDeletedRowsAreNotLocked() throws Exception {
        Instance tenantB = start(null), deleted = start(null);
        jdbc.update("update flow_instance set tenant_id='B' where id=?", tenantB.getId());
        jdbc.update("update flow_instance set tenant_id='A', del_flag='1' where id=?", deleted.getId());
        beans.put(org.dromara.warm.flow.core.spi.TenantHandler.class,
            (org.dromara.warm.flow.core.spi.TenantHandler) () -> "A");
        FlowEngine.initTenantHandler(null);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            for (Long id : List.of(tenantB.getId(), deleted.getId())) {
                template.execute(status -> {
                    assertThrows(FlowException.class, () -> instances.unActive(id));
                    Future<Integer> otherTenant = pool.submit(() ->
                        jdbc.update("update flow_instance set update_time=update_time where id=?", id));
                    try { assertEquals(1, otherTenant.get(1, TimeUnit.SECONDS).intValue()); }
                    catch (Exception e) { throw new AssertionError("不可见行不能被持锁至外层事务结束", e); }
                    status.setRollbackOnly(); return null;
                });
            }
        } finally {
            pool.shutdownNow();
            beans.remove(org.dromara.warm.flow.core.spi.TenantHandler.class);
            FlowEngine.initTenantHandler(null);
        }
    }

    @Test
    void restartAndResetUseWorkflowWrapper() throws Exception {
        for (boolean reset : List.of(false, true)) {
            Instance instance = rejected();
            TerminateCommand stop = new TerminateCommand();
            stop.setTaskId(current(instance).getId()); stop.setOperator(operator());
            FlowEngine.workflow().terminate(stop);
            RestartCommand command = reset ? new ResetCommand() : new RestartCommand();
            command.setInstanceId(instance.getId()); command.setTargetNodeCode("A");
            command.setHandlers(List.of("user")); command.setOperator(operator());
            TransactionalWorkflowService plain = new TransactionalWorkflowService(null);
            if (reset) {
                assertThrows(FlowException.class, () -> plain.reset((ResetCommand) command));
                FlowEngine.workflow().reset((ResetCommand) command);
            } else {
                assertThrows(FlowException.class, () -> plain.restart(command));
                FlowEngine.workflow().restart(command);
            }
            assertEquals("A", current(instance).getNodeCode());
        }
    }

    @Test
    void instanceVariableMutationReloadsUnderLock() throws Exception {
        Instance instance = rejected();
        Long a = current(instance).getId();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> approve = pool.submit(() -> tasks.execute(a, context(), SkipType.PASS.getKey()));
            Future<?> mutate = pool.submit(() -> instances.removeVariables(instance.getId(), "v"));
            approve.get(5, TimeUnit.SECONDS); mutate.get(5, TimeUnit.SECONDS);
            assertEquals("D", current(instance).getNodeCode());
            assertFalse(FlowEngine.insService().getById(instance.getId()).getDefJson().contains("__rejectReturnRuntime"));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void failureRollsBackTasksHistoryUsersAndSnapshot() {
        for (String stage : List.of("task", "history", "instance", "user")) {
            failStage.set(null);
            Instance instance = rejected();
            Long a = current(instance).getId();
            String before = FlowEngine.insService().getById(instance.getId()).getDefJson();
            int history = count("flow_his_task"), users = count("flow_user"), taskCount = count("flow_task");
            failStage.set(stage);
            assertThrows(IllegalStateException.class, () -> tasks.execute(a, context(), SkipType.PASS.getKey()), stage);
            assertEquals(a, current(instance).getId());
            assertEquals(before, FlowEngine.insService().getById(instance.getId()).getDefJson());
            assertEquals(history, count("flow_his_task")); assertEquals(users, count("flow_user"));
            assertEquals(taskCount, count("flow_task"));
        }
    }

    private void failIf(String stage) {
        if (stage.equals(failStage.get())) { throw new IllegalStateException("injected " + stage + " write failure"); }
    }

    @Test
    void completeCompetingWithTerminateDoesNotResurrectPendingRuntime() throws Exception {
        Instance instance = rejected();
        Long a = current(instance).getId();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> approve = pool.submit(() -> {
                try { tasks.execute(a, context(), SkipType.PASS.getKey()); }
                catch (FlowException expectedIfTerminatedFirst) { }
            });
            Future<?> terminate = pool.submit(() -> tasks.terminateByInstanceId(instance.getId(), context()));
            approve.get(5, TimeUnit.SECONDS); terminate.get(5, TimeUnit.SECONDS);
            assertTrue(tasks.getByInsId(instance.getId()).isEmpty());
            Instance ended = instances.getById(instance.getId());
            assertEquals(2, ended.getNodeType());
            assertFalse(ended.getDefJson().contains("__rejectReturnRuntime"));
        } finally { pool.shutdownNow(); }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void noTransactionGuardsEveryInstanceWriteAndEventStart() {
        Instance instance = rejected();
        Long a = current(instance).getId();
        TransactionalInsService plain = new TransactionalInsService(null);
        plain.setDao((FlowInstanceDao) new FlowInstanceDaoImpl());
        TransactionalTaskService plainTasks = new TransactionalTaskService(null);
        plainTasks.setDao((FlowTaskDao) new FlowTaskDaoImpl());
        List<Runnable> writes = List.of(() -> plain.active(instance.getId()), () -> plain.unActive(instance.getId()),
            () -> plain.removeVariables(instance.getId(), "x"), () -> plainTasks.revoke(instance.getId(), context()),
            () -> plainTasks.terminateByInstanceId(instance.getId(), context()),
            () -> plainTasks.terminateByTaskId(a, context()),
            () -> plainTasks.updateHandlers(a, context(), List.of("other"), List.of(), "transfer"),
            () -> plain.startByMessage("b", "flow0", "event", Map.of(), context()),
            () -> plain.startByTimer("b", instance.getDefinitionId(), "D", Map.of(), context()));
        for (Runnable write : writes) {
            assertTrue(assertThrows(FlowException.class, write::run).getMessage().contains("事务管理器"));
        }
        assertEquals(a, current(instance).getId());
    }

    @Test
    void beanFactoriesAlwaysRegisterGuardedServices() {
        FlowAutoConfig config = new FlowAutoConfig();
        assertInstanceOf(TransactionalTaskService.class, config.taskService(null, new org.springframework.beans.factory.support.DefaultListableBeanFactory().getBeanProvider(org.springframework.transaction.PlatformTransactionManager.class)));
        assertInstanceOf(TransactionalInsService.class, config.instanceService(null, new org.springframework.beans.factory.support.DefaultListableBeanFactory().getBeanProvider(org.springframework.transaction.PlatformTransactionManager.class)));
        assertInstanceOf(TransactionalWorkflowService.class, config.workflowService());
    }

    private Instance rejected() {
        Instance instance = start(DIRECT);
        for (int i = 0; i < 3; i++) { tasks.execute(current(instance).getId(), context(), SkipType.PASS.getKey()); }
        tasks.execute(current(instance).getId(), context(), SkipType.REJECT.getKey());
        return instance;
    }

    private Instance start(String config) {
        Definition def = definition(config);
        return instances.start("biz" + def.getId(), def.getFlowCode(), context());
    }

    private Definition definition(String config) {
        Definition def = FlowEngine.newDef().setFlowCode("flow" + count("flow_definition")).setFlowName("test").setVersion("1")
            .setIsPublish(1).setActivityStatus(1);
        FlowEngine.defService().save(def);
        List<String> codes = List.of("start", "A", "B", "C", "D", "end");
        for (String code : codes) {
            int type = "start".equals(code) ? 0 : "end".equals(code) ? 2 : 1;
            FlowEngine.nodeService().save(FlowEngine.newNode().setDefinitionId(def.getId()).setNodeCode(code)
                .setNodeName(code).setNodeType(type).setNodeRatio("0").setPermissionFlag("user")
                .setNodeConfig("D".equals(code) ? config : null));
        }
        for (int i = 0; i < codes.size() - 1; i++) {
            edge(def, codes.get(i), codes.get(i + 1), SkipType.PASS.getKey());
        }
        edge(def, "D", "A", SkipType.REJECT.getKey());
        return def;
    }

    private void edge(Definition def, String from, String to, String type) {
        FlowEngine.skipService().save(FlowEngine.newSkip().setDefinitionId(def.getId()).setNowNodeCode(from)
            .setNextNodeCode(to).setNowNodeType("start".equals(from) ? 0 : 1)
            .setNextNodeType("end".equals(to) ? 2 : 1).setSkipType(type));
    }
    private Task current(Instance instance) {
        return FlowEngine.taskService().getByInsId(instance.getId()).stream().findFirst().orElseThrow();
    }
    private int count(String table) { return jdbc.queryForObject("select count(*) from " + table, Integer.class); }
    private WorkflowContext context() {
        WorkflowContext context = new WorkflowContext(); context.setHandler("user"); context.setIgnorePermission(true);
        return context;
    }
    private OperatorContext operator() { OperatorContext value = new OperatorContext("user"); value.setIgnorePermission(true); return value; }
    private static String snake(String value) { return value.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT); }

    /** 从 ORM 实体生成仅用于本测试的 H2 表；实际读写仍由生产 Mapper SQL 执行。 */
    private void createTable(Class<?> type, String table) {
        Map<String, String> columns = new TreeMap<>();
        for (Method method : type.getMethods()) {
            if (method.getParameterCount() != 0 || !method.getName().startsWith("get") || method.getName().equals("getClass")) { continue; }
            String name = method.getName().substring(3);
            Class<?> value = method.getReturnType();
            String sql = value == String.class ? "varchar(16000)" : value == Long.class ? "bigint"
                : value == Integer.class ? "int" : value == Boolean.class ? "boolean"
                : Date.class.isAssignableFrom(value) ? "timestamp" : null;
            if (sql != null) { columns.put(snake(name), sql); }
        }
        columns.put("id", "bigint primary key");
        columns.put("del_flag", "varchar(1) default '0'");
        jdbc.execute("create table " + table + " (" + columns.entrySet().stream()
            .map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(",")) + ")");
    }
}

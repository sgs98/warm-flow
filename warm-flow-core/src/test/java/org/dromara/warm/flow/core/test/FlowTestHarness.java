package org.dromara.warm.flow.core.test;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.config.WarmFlow;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Form;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Skip;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.invoker.FrameInvoker;
import org.dromara.warm.flow.core.listener.GlobalListener;
import org.dromara.warm.flow.core.service.ChartService;
import org.dromara.warm.flow.core.service.DefService;
import org.dromara.warm.flow.core.service.FormService;
import org.dromara.warm.flow.core.service.HisTaskService;
import org.dromara.warm.flow.core.service.InsService;
import org.dromara.warm.flow.core.service.NodeService;
import org.dromara.warm.flow.core.service.SkipService;
import org.dromara.warm.flow.core.service.TaskService;
import org.dromara.warm.flow.core.service.UserService;
import org.dromara.warm.flow.core.service.impl.ChartServiceImpl;
import org.dromara.warm.flow.core.service.impl.DefServiceImpl;
import org.dromara.warm.flow.core.service.impl.FormServiceImpl;
import org.dromara.warm.flow.core.service.impl.HisTaskServiceImpl;
import org.dromara.warm.flow.core.service.impl.InsServiceImpl;
import org.dromara.warm.flow.core.service.impl.NodeServiceImpl;
import org.dromara.warm.flow.core.service.impl.SkipServiceImpl;
import org.dromara.warm.flow.core.service.impl.TaskServiceImpl;
import org.dromara.warm.flow.core.service.impl.UserServiceImpl;
import org.dromara.warm.flow.core.test.dao.DefinitionMemDao;
import org.dromara.warm.flow.core.test.dao.FormMemDao;
import org.dromara.warm.flow.core.test.dao.HisTaskMemDao;
import org.dromara.warm.flow.core.test.dao.InstanceMemDao;
import org.dromara.warm.flow.core.test.dao.NodeMemDao;
import org.dromara.warm.flow.core.test.dao.SkipMemDao;
import org.dromara.warm.flow.core.test.dao.TaskMemDao;
import org.dromara.warm.flow.core.test.dao.UserMemDao;
import org.dromara.warm.flow.core.test.entity.TestDefinition;
import org.dromara.warm.flow.core.test.entity.TestForm;
import org.dromara.warm.flow.core.test.entity.TestHisTask;
import org.dromara.warm.flow.core.test.entity.TestInstance;
import org.dromara.warm.flow.core.test.entity.TestNode;
import org.dromara.warm.flow.core.test.entity.TestSkip;
import org.dromara.warm.flow.core.test.entity.TestTask;
import org.dromara.warm.flow.core.test.entity.TestUser;
import org.dromara.warm.flow.core.test.listener.RecordingGlobalListener;
import org.dromara.warm.flow.core.test.listener.RecordingListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 纯 JVM 单测装配：内存 DAO + FrameInvoker 手工接线，不依赖 Spring/数据库。
 * FlowEngine 的服务静态字段为 final null（无缓存），每次取服务都会重新走
 * FrameInvoker.getBean，因此每个测试可独立创建 harness 重建注册表。
 * 特征测试依赖 JUnit 默认的单线程顺序执行，勿开启并行。
 *
 * @author warm
 */
public class FlowTestHarness implements AutoCloseable {

    /**
     * DAO 调用流水，供查询次数与持久化顺序断言。
     */
    public final List<String> daoLog = new ArrayList<>();

    /**
     * 内存待办任务 DAO。
     */
    public final TaskMemDao<Task> taskDao = new TaskMemDao<>(FlowEngine::newTask, daoLog);
    /**
     * 内存流程用户 DAO。
     */
    public final UserMemDao<User> userDao = new UserMemDao<>(FlowEngine::newUser, daoLog);
    /**
     * 内存历史任务 DAO。
     */
    public final HisTaskMemDao<HisTask> hisTaskDao = new HisTaskMemDao<>(FlowEngine::newHisTask, daoLog);
    /**
     * 内存流程实例 DAO。
     */
    public final InstanceMemDao<Instance> insDao = new InstanceMemDao<>(FlowEngine::newIns, daoLog);
    /**
     * 内存流程定义 DAO。
     */
    public final DefinitionMemDao<Definition> defDao = new DefinitionMemDao<>(FlowEngine::newDef, daoLog);
    /**
     * 内存流程节点 DAO。
     */
    public final NodeMemDao<Node> nodeDao = new NodeMemDao<>(FlowEngine::newNode, daoLog);
    /**
     * 内存节点跳转 DAO。
     */
    public final SkipMemDao<Skip> skipDao = new SkipMemDao<>(FlowEngine::newSkip, daoLog);
    /**
     * 内存流程表单 DAO。
     */
    public final FormMemDao<Form> formDao = new FormMemDao<>(FlowEngine::newForm, daoLog);

    private final Map<Class<?>, Object> registry = new HashMap<>();

    /**
     * 创建内存 DAO、服务实现和 FrameInvoker 注册表，并重置全局测试扩展点。
     */
    public FlowTestHarness() {
        FlowEngine.setNewDef(TestDefinition::new);
        FlowEngine.setNewNode(TestNode::new);
        FlowEngine.setNewSkip(TestSkip::new);
        FlowEngine.setNewIns(TestInstance::new);
        FlowEngine.setNewTask(TestTask::new);
        FlowEngine.setNewHisTask(TestHisTask::new);
        FlowEngine.setNewUser(TestUser::new);
        FlowEngine.setNewForm(TestForm::new);

        FrameInvoker.setBeanFunction(clazz -> resolveBean(clazz));

        DefServiceImpl defService = new DefServiceImpl();
        defService.setDao(defDao);
        NodeServiceImpl nodeService = new NodeServiceImpl();
        nodeService.setDao(nodeDao);
        SkipServiceImpl skipService = new SkipServiceImpl();
        skipService.setDao(skipDao);
        InsServiceImpl insService = new InsServiceImpl();
        insService.setDao(insDao);
        TaskServiceImpl taskService = new TaskServiceImpl();
        taskService.setDao(taskDao);
        HisTaskServiceImpl hisTaskService = new HisTaskServiceImpl();
        hisTaskService.setDao(hisTaskDao);
        UserServiceImpl userService = new UserServiceImpl();
        userService.setDao(userDao);
        FormServiceImpl formService = new FormServiceImpl();
        formService.setDao(formDao);

        registry.put(DefService.class, defService);
        registry.put(NodeService.class, nodeService);
        registry.put(SkipService.class, skipService);
        registry.put(InsService.class, insService);
        registry.put(TaskService.class, taskService);
        registry.put(HisTaskService.class, hisTaskService);
        registry.put(UserService.class, userService);
        registry.put(FormService.class, formService);
        registry.put(ChartService.class, new ChartServiceImpl());
        registry.put(org.dromara.warm.flow.core.workflow.WorkflowService.class
                , new org.dromara.warm.flow.core.workflow.WorkflowServiceImpl());
        registry.put(GlobalListener.class, RecordingGlobalListener.instance());

        FlowEngine.jsonConvert = new FlatJsonConvert();
        FlowEngine.initDataFillHandler(null);
        FlowEngine.initGlobalListener(null);
        FlowEngine.initTenantHandler(null);
        FlowEngine.initPermissionHandler(null);
        FlowEngine.setFlowConfig(new WarmFlow());

        RecordingListener.EVENTS.clear();
        RecordingGlobalListener.EVENTS.clear();
    }

    /**
     * 清空 DAO 调用流水和监听事件，供同一测试分阶段断言。
     */
    public void reset() {
        daoLog.clear();
        RecordingListener.EVENTS.clear();
        RecordingGlobalListener.EVENTS.clear();
    }

    /**
     * 注册测试扩展点，供 FrameInvoker 在引擎初始化处理器时回退获取。
     *
     * @param type Bean 类型
     * @param bean 测试 Bean
     * @param <T> Bean 类型
     */
    public <T> void register(Class<T> type, T bean) {
        registry.put(type, bean);
    }

    private Object resolveBean(Class<?> clazz) {
        Object bean = registry.get(clazz);
        if (bean != null) {
            return bean;
        }
        if (clazz.isInterface()) {
            return null;
        }
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * 解除当前测试的 FrameInvoker Bean 查找和监听器状态。
     */
    @Override
    public void close() {
        FrameInvoker.setBeanFunction(clazz -> null);
        RecordingListener.EVENTS.clear();
        RecordingGlobalListener.EVENTS.clear();
    }
}

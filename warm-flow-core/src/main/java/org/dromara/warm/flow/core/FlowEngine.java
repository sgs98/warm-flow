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
package org.dromara.warm.flow.core;

import org.dromara.warm.flow.core.config.WarmFlow;
import org.dromara.warm.flow.core.entity.*;
import org.dromara.warm.flow.core.handler.DataFillHandler;
import org.dromara.warm.flow.core.handler.PermissionHandler;
import org.dromara.warm.flow.core.handler.TenantHandler;
import org.dromara.warm.flow.core.invoker.FrameInvoker;
import org.dromara.warm.flow.core.json.JsonConvert;
import org.dromara.warm.flow.core.listener.GlobalListener;
import org.dromara.warm.flow.core.service.*;
import org.dromara.warm.flow.core.utils.ClassUtil;
import org.dromara.warm.flow.core.utils.ObjectUtil;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.dromara.warm.flow.core.workflow.WorkflowService;

import java.lang.reflect.Constructor;
import java.util.function.Supplier;

/**
 * 流程引擎
 *
 * @author warm
 */
public class FlowEngine {

    private static Supplier<Definition> defSupplier;
    private static Supplier<Node> nodeSupplier;
    private static Supplier<Skip> skipSupplier;
    private static Supplier<Instance> insSupplier;
    private static Supplier<Task> taskSupplier;
    private static Supplier<HisTask> hisTaskSupplier;
    private static Supplier<User> userSupplier;
    private static Supplier<Form> formSupplier;

    private static WarmFlow flowConfig;

    private static DataFillHandler dataFillHandler;

    private static TenantHandler tenantHandler;

    private static PermissionHandler permissionHandler;

    private static GlobalListener globalListener;

    /**
     * JSON 转换器 SPI 实例，由 {@link WarmFlow#spiLoad()} 装配。
     */
    public static JsonConvert jsonConvert;

    /**
     * 获取流程定义服务。
     *
     * @return 流程定义服务
     */
    public static DefService defService() {
        return FrameInvoker.getBean(DefService.class);
    }

    /**
     * 获取流程节点服务。
     *
     * @return 流程节点服务
     */
    public static NodeService nodeService() {
        return FrameInvoker.getBean(NodeService.class);
    }

    /**
     * 获取节点跳转服务。
     *
     * @return 节点跳转服务
     */
    public static SkipService skipService() {
        return FrameInvoker.getBean(SkipService.class);
    }

    /**
     * 获取流程实例服务。
     *
     * @return 流程实例服务
     */
    public static InsService insService() {
        return FrameInvoker.getBean(InsService.class);
    }

    /**
     * 获取待办任务服务。
     *
     * @return 待办任务服务
     */
    public static TaskService taskService() {
        return FrameInvoker.getBean(TaskService.class);
    }

    /**
     * 获取历史任务服务。
     *
     * @return 历史任务服务
     */
    public static HisTaskService hisTaskService() {
        return FrameInvoker.getBean(HisTaskService.class);
    }

    /**
     * 获取流程用户服务。
     *
     * @return 流程用户服务
     */
    public static UserService userService() {
        return FrameInvoker.getBean(UserService.class);
    }

    /**
     * 获取流程表单服务。
     *
     * @return 流程表单服务
     */
    public static FormService formService() {
        return FrameInvoker.getBean(FormService.class);
    }

    /**
     * 获取流程图元数据服务。
     *
     * @return 流程图元数据服务
     */
    public static ChartService chartService() {
        return FrameInvoker.getBean(ChartService.class);
    }

    /**
     * 获取流程操作统一门面。
     *
     * @return 流程操作服务
     */
    public static WorkflowService workflow() {
        return FrameInvoker.getBean(WorkflowService.class);
    }

    /**
     * 注册流程定义实体工厂。
     *
     * @param supplier 流程定义实体供应器
     */
    public static void setNewDef(Supplier<Definition> supplier) {
        FlowEngine.defSupplier = supplier;
    }

    /**
     * 创建流程定义实体。
     *
     * @return 流程定义实体
     */
    public static Definition newDef() {
        return defSupplier.get();
    }

    /**
     * 注册流程节点实体工厂。
     *
     * @param supplier 流程节点实体供应器
     */
    public static void setNewNode(Supplier<Node> supplier) {
        FlowEngine.nodeSupplier = supplier;
    }

    /**
     * 创建流程节点实体。
     *
     * @return 流程节点实体
     */
    public static Node newNode() {
        return nodeSupplier.get();
    }

    /**
     * 注册节点跳转实体工厂。
     *
     * @param supplier 节点跳转实体供应器
     */
    public static void setNewSkip(Supplier<Skip> supplier) {
        FlowEngine.skipSupplier = supplier;
    }

    /**
     * 创建节点跳转实体。
     *
     * @return 节点跳转实体
     */
    public static Skip newSkip() {
        return skipSupplier.get();
    }

    /**
     * 注册流程实例实体工厂。
     *
     * @param supplier 流程实例实体供应器
     */
    public static void setNewIns(Supplier<Instance> supplier) {
        FlowEngine.insSupplier = supplier;
    }

    /**
     * 创建流程实例实体。
     *
     * @return 流程实例实体
     */
    public static Instance newIns() {
        return insSupplier.get();
    }

    /**
     * 注册待办任务实体工厂。
     *
     * @param supplier 待办任务实体供应器
     */
    public static void setNewTask(Supplier<Task> supplier) {
        FlowEngine.taskSupplier = supplier;
    }

    /**
     * 创建待办任务实体。
     *
     * @return 待办任务实体
     */
    public static Task newTask() {
        return taskSupplier.get();
    }

    /**
     * 注册历史任务实体工厂。
     *
     * @param supplier 历史任务实体供应器
     */
    public static void setNewHisTask(Supplier<HisTask> supplier) {
        FlowEngine.hisTaskSupplier = supplier;
    }

    /**
     * 创建历史任务实体。
     *
     * @return 历史任务实体
     */
    public static HisTask newHisTask() {
        return hisTaskSupplier.get();
    }

    /**
     * 注册流程用户实体工厂。
     *
     * @param supplier 流程用户实体供应器
     */
    public static void setNewUser(Supplier<User> supplier) {
        FlowEngine.userSupplier = supplier;
    }

    /**
     * 创建流程用户实体。
     *
     * @return 流程用户实体
     */
    public static User newUser() {
        return userSupplier.get();
    }

    /**
     * 注册流程表单实体工厂。
     *
     * @param supplier 流程表单实体供应器
     */
    public static void setNewForm(Supplier<Form> supplier) {
        FlowEngine.formSupplier = supplier;
    }

    /**
     * 创建流程表单实体。
     *
     * @return 流程表单实体
     */
    public static Form newForm() {
        return formSupplier.get();
    }

    /**
     * 获取引擎配置。
     *
     * @return 引擎配置
     */
    public static WarmFlow getFlowConfig() {
        return FlowEngine.flowConfig;
    }

    /**
     * 设置引擎配置。
     *
     * @param flowConfig 引擎配置
     */
    public static void setFlowConfig(WarmFlow flowConfig) {
        FlowEngine.flowConfig = flowConfig;
    }

    /**
     * 初始化数据填充处理器。
     *
     * @param handlerPath 处理器类路径，为空时使用默认实现或容器中的 Bean
     */
    public static void initDataFillHandler(String handlerPath) {
        dataFillHandler = initBean(DataFillHandler.class, handlerPath, () -> new DataFillHandler() {
        });
    }

    /**
     * 初始化租户处理器。
     *
     * @param handlerPath 处理器类路径，为空时尝试从容器获取
     */
    public static void initTenantHandler(String handlerPath) {
        tenantHandler = initBean(TenantHandler.class, handlerPath, null);
    }

    /**
     * 初始化办理人权限处理器。
     *
     * @param handlerPath 处理器类路径，为空时尝试从容器获取
     */
    public static void initPermissionHandler(String handlerPath) {
        permissionHandler = initBean(PermissionHandler.class, handlerPath, null);
    }

    /**
     * 初始化全局监听器。
     *
     * @param handlerPath 监听器类路径，为空时尝试从容器获取
     */
    public static void initGlobalListener(String handlerPath) {
        globalListener = initBean(GlobalListener.class, handlerPath, null);
    }

    /**
     * 获取填充类
     *
     * @return 数据填充处理器
     */
    public static DataFillHandler dataFillHandler() {
        return dataFillHandler;
    }

    /**
     * 获取办理人权限处理器。
     *
     * @return 办理人权限处理器
     */
    public static PermissionHandler permissionHandler() {
        return permissionHandler;
    }

    /**
     * 获取租户数据
     *
     * @return 租户处理器
     */
    public static TenantHandler tenantHandler() {
        return tenantHandler;
    }

    /**
     * 获取全局监听器
     *
     * @return 全局监听器
     */
    public static GlobalListener globalListener() {
        return globalListener;
    }

    /**
     * 获取数据库类型
     *
     * @return 数据库类型
     */
    public static String dataSourceType() {
        return flowConfig.getDataSourceType();
    }

    /**
     * 获取对象：候选对象非空时直接返回，否则从 {@link FrameInvoker} 注册表解析。
     *
     * @param t      候选对象
     * @param tClass 目标类型
     * @param <T>    对象类型
     * @return 候选对象或注册表实例
     */
    public static <T> T getObj(T t, Class<T> tClass) {
        if (ObjectUtil.isNotNull(t)) {
            return t;
        }
        t = FrameInvoker.getBean(tClass);
        return t;
    }

    /**
     * 初始化bean，先从yml配置获取bean的全包名路径，否则从spring容器获取bean，如果都没有，则通过supplier获取bean
     *
     * @param tClazz   bean的class类型
     * @param beanPath bean全包名路径
     * @param supplier 获取bean的lambda
     * @param <T>      bean类型
     * @return bean
     */
    private static <T> T initBean(Class<T> tClazz, String beanPath, Supplier<T> supplier) {
        T hander = null;
        try {
            if (!StringUtils.isEmpty(beanPath)) {
                Class<?> clazz = ClassUtil.getClazz(beanPath);
                if (clazz != null && tClazz.isAssignableFrom(clazz)) {
                    Constructor<?> constructor = clazz.getConstructor();
                    hander = tClazz.cast(constructor.newInstance());
                }
            }
        } catch (Exception ignored) {
        }
        if (hander == null) {
            hander = FrameInvoker.getBean(tClazz);
        }
        if (hander == null && supplier != null) {
            hander = supplier.get();
        }
        return hander;
    }

}

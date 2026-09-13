package org.dromara.warm.flow.spring.boot.config;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.config.WarmFlow;
import org.dromara.warm.flow.core.orm.dao.*;
import org.dromara.warm.flow.core.service.*;
import org.dromara.warm.flow.core.service.impl.*;
import org.dromara.warm.flow.core.utils.IdUtils;
import org.dromara.warm.flow.core.workflow.WorkflowService;
import org.dromara.warm.flow.core.workflow.WorkflowServiceImpl;
import org.dromara.warm.flow.orm.dao.*;
import org.dromara.warm.flow.orm.entity.*;
import org.dromara.warm.flow.orm.keygen.MybatisPlusIdGen;
import org.dromara.warm.plugin.modes.sb.config.BeanConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 3 MyBatis-Plus 工作流自动配置。
 *
 * @author may
 */
@Configuration
@ConditionalOnProperty(value = "warm-flow.enabled", havingValue = "true", matchIfMissing = true)
@MapperScan("org.dromara.warm.flow.orm.mapper")
public class FlowAutoConfig extends BeanConfig {

    @Bean
    public FlowDefinitionDao definitionDao() {
        return new FlowDefinitionDaoImpl();
    }

    @Bean
    public DefService definitionService(FlowDefinitionDao dao) {
        return new DefServiceImpl().setDao(dao);
    }

    @Bean
    public FlowNodeDao nodeDao() {
        return new FlowNodeDaoImpl();
    }

    @Bean
    public NodeService nodeService(FlowNodeDao dao) {
        return new NodeServiceImpl().setDao(dao);
    }

    @Bean
    public FlowSkipDao skipDao() {
        return new FlowSkipDaoImpl();
    }

    @Bean
    public SkipService skipService(FlowSkipDao dao) {
        return new SkipServiceImpl().setDao(dao);
    }

    @Bean
    public FlowInstanceDao instanceDao() {
        return new FlowInstanceDaoImpl();
    }

    @Bean
    public InsService instanceService(FlowInstanceDao dao) {
        return new InsServiceImpl().setDao(dao);
    }

    @Bean
    public FlowTaskDao taskDao() {
        return new FlowTaskDaoImpl();
    }

    @Bean
    public TaskService taskService(FlowTaskDao dao) {
        return new TaskServiceImpl().setDao(dao);
    }

    /**
     * 注册流程统一操作门面。
     *
     * @return 流程操作服务
     */
    @Bean
    public WorkflowService workflowService() {
        return new WorkflowServiceImpl();
    }

    @Bean
    public FlowHisTaskDao hisTaskDao() {
        return new FlowHisTaskDaoImpl();
    }

    @Bean
    public HisTaskService hisTaskService(FlowHisTaskDao dao) {
        return new HisTaskServiceImpl().setDao(dao);
    }

    @Bean
    public FlowUserDao flowUserDao() {
        return new FlowUserDaoImpl();
    }

    @Bean
    public UserService flowUserService(FlowUserDao dao) {
        return new UserServiceImpl().setDao(dao);
    }

    @Bean
    public FlowFormDao formDao() {
        return new FlowFormDaoImpl();
    }

    @Bean
    public FormService flowFormService(FlowFormDao dao) {
        return new FormServiceImpl().setDao(dao);
    }

    @Override
    public void setNewEntity() {
        FlowEngine.setNewDef(FlowDefinition::new);
        FlowEngine.setNewIns(FlowInstance::new);
        FlowEngine.setNewHisTask(FlowHisTask::new);
        FlowEngine.setNewNode(FlowNode::new);
        FlowEngine.setNewSkip(FlowSkip::new);
        FlowEngine.setNewTask(FlowTask::new);
        FlowEngine.setNewUser(FlowUser::new);
        FlowEngine.setNewForm(FlowForm::new);
    }

    @Override
    public void after(WarmFlow flowConfig) {
        IdUtils.setInstanceNative(new MybatisPlusIdGen());
    }
}

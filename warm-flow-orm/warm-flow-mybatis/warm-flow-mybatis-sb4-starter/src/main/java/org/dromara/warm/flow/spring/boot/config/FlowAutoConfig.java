package org.dromara.warm.flow.spring.boot.config;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.config.WarmFlow;
import org.dromara.warm.flow.core.orm.dao.*;
import org.dromara.warm.flow.core.service.*;
import org.dromara.warm.flow.core.service.impl.*;
import org.dromara.warm.flow.orm.dao.*;
import org.dromara.warm.flow.orm.entity.*;
import org.dromara.warm.flow.orm.utils.CommonUtil;
import org.dromara.warm.plugin.modes.sb.config.BeanConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Boot 4 MyBatis 工作流自动配置。
 *
 * @author may
 */
@Configuration
@ConditionalOnProperty(value = "warm-flow.enabled", havingValue = "true", matchIfMissing = true)
@MapperScan("org.dromara.warm.flow.orm.mapper")
public class FlowAutoConfig extends BeanConfig {

    private final SqlSessionFactory sqlSessionFactory;

    public FlowAutoConfig(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

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
        loadXml(sqlSessionFactory);
        CommonUtil.setDataSourceType(flowConfig, sqlSessionFactory.getConfiguration());
    }

    private void loadXml(SqlSessionFactory factory) {
        List<String> mapperList = Arrays.asList("warm/flow/FlowDefinitionMapper.xml", "warm/flow/FlowHisTaskMapper.xml",
            "warm/flow/FlowInstanceMapper.xml", "warm/flow/FlowNodeMapper.xml", "warm/flow/FlowFormMapper.xml",
            "warm/flow/FlowSkipMapper.xml", "warm/flow/FlowTaskMapper.xml", "warm/flow/FlowUserMapper.xml");
        org.apache.ibatis.session.Configuration configuration = factory.getConfiguration();
        configuration.setJdbcTypeForNull(JdbcType.NULL);
        try {
            for (String mapper : mapperList) {
                new XMLMapperBuilder(Resources.getResourceAsStream(mapper), configuration,
                    getClass().getResource("/") + mapper, configuration.getSqlFragments()).parse();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

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
 * 工作流bean注册配置
 *
 * @author warm
 * @since 2023/6/5 23:01
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

    private void loadXml(SqlSessionFactory sqlSessionFactory) {
        List<String> mapperList = Arrays.asList("warm/flow/FlowDefinitionMapper.xml", "warm/flow/FlowHisTaskMapper.xml"
            , "warm/flow/FlowInstanceMapper.xml", "warm/flow/FlowNodeMapper.xml", "warm/flow/FlowFormMapper.xml"
            , "warm/flow/FlowSkipMapper.xml", "warm/flow/FlowTaskMapper.xml", "warm/flow/FlowUserMapper.xml");
        org.apache.ibatis.session.Configuration configuration = sqlSessionFactory.getConfiguration();
        configuration.setJdbcTypeForNull(JdbcType.NULL);
        try {
            for (String mapper : mapperList) {
                XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(Resources.getResourceAsStream(mapper),
                    configuration, getClass().getResource("/") + mapper, configuration.getSqlFragments());
                xmlMapperBuilder.parse();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

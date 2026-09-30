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
package org.dromara.warm.flow.orm.dao;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.dromara.warm.flow.orm.entity.FlowDefinition;
import org.dromara.warm.flow.orm.mapper.FlowDefinitionMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 设计器「最小载荷」保存验证：只带 id/flowCode/modelValue 的定义实体走 updateById 时，
 * 仅回写 model_value，不覆盖 flow_name/category/version 等宿主维护的定义字段。
 * 该行为是嵌入模式（onlyDesignShow）下切换设计器模型后落库的前提。
 *
 * @author warm
 * @since 2026/9/30
 */
class DefPartialUpdateTest {

    private static final Long DEF_ID = 200L;
    private static DataSource dataSource;
    private static SqlSession session;

    private static final FlowDefinitionDaoImpl defDao = new FlowDefinitionDaoImpl() {
        @Override
        public FlowDefinitionMapper getMapper() {
            return session.getMapper(FlowDefinitionMapper.class);
        }
    };

    @BeforeAll
    static void init() throws Exception {
        dataSource = new UnpooledDataSource("org.h2.Driver",
            "jdbc:h2:mem:flow_def_partial_update_test;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE flow_definition (id bigint primary key, create_time timestamp,"
                + " update_time timestamp, create_by varchar(20), update_by varchar(20), tenant_id varchar(40),"
                + " del_flag char(1) default '0', flow_code varchar(40), flow_name varchar(100),"
                + " model_value varchar(40) default 'CLASSICS', category varchar(100), version varchar(20),"
                + " is_publish int, form_custom char(1), form_path varchar(100), activity_status int,"
                + " listener_type varchar(100), listener_path varchar(100), ext varchar(100))");
            st.execute("INSERT INTO flow_definition (id, del_flag, flow_code, flow_name, model_value,"
                + " category, version, form_custom) VALUES (" + DEF_ID
                + ", '0', 'leave6', '请假申请-排他并行会签', 'CLASSICS', '1762300000000000100', '3', 'N')");
        }

        GlobalConfig globalConfig = GlobalConfigUtils.defaults();
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(FlowDefinitionMapper.class);
        SqlSessionFactory factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
        session = factory.openSession(true);
    }

    /**
     * 嵌入模式切换模型时提交的最小载荷，只更新 model_value。
     */
    @Test
    void partialUpdateOnlyWritesModelValue() {
        defDao.updateById(new FlowDefinition()
            .setId(DEF_ID)
            .setFlowCode("leave6")
            .setModelValue("MIMIC"));

        assertEquals("MIMIC", raw("model_value"));
        assertEquals("请假申请-排他并行会签", raw("flow_name"), "宿主维护的流程名不被覆盖");
        assertEquals("1762300000000000100", raw("category"));
        assertEquals("3", raw("version"));
        assertEquals("N", raw("form_custom"));
    }

    private static String raw(String column) {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT " + column + " FROM flow_definition WHERE id = " + DEF_ID)) {
            rs.next();
            return rs.getString(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

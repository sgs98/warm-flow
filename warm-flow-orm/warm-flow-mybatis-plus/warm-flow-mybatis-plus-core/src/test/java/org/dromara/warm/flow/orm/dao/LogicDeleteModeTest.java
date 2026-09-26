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
import org.dromara.warm.flow.orm.entity.FlowNode;
import org.dromara.warm.flow.orm.handler.WarmFlowPostInitTableInfoHandler;
import org.dromara.warm.flow.orm.mapper.FlowNodeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MyBatis-Plus 逻辑删除/物理删除模式验证：
 * 默认（logic-delete=true）删除走 MP @TableLogic 逻辑删除；
 * logic-delete=false 时 warm-flow 注册 {@link WarmFlowPostInitTableInfoHandler}
 * 关闭流程表 withLogicDelete，删除变为物理删除，不产生 del_flag='1' 尸体行。
 *
 * 注意：withLogicDelete 必须在 mapper 注册（TableInfo 初始化）前关闭，
 * 运行时由 MP 在构建 TableInfo 时回调 handler。
 *
 * @author warm
 * @since 2026/9/26
 */
class LogicDeleteModeTest {

    private static final Long DEF_ID = 100L;
    private static DataSource dataSource;
    private static SqlSession logicSession;
    private static SqlSession physicalSession;
    // 分别绑定逻辑删/物理删两个工厂的 mapper（覆盖 getMapper，不走 FrameInvoker）
    private static final FlowNodeDaoImpl logicDao = new FlowNodeDaoImpl() {
        @Override
        public FlowNodeMapper getMapper() {
            return logicSession.getMapper(FlowNodeMapper.class);
        }
    };
    private static final FlowNodeDaoImpl physicalDao = new FlowNodeDaoImpl() {
        @Override
        public FlowNodeMapper getMapper() {
            return physicalSession.getMapper(FlowNodeMapper.class);
        }
    };

    @BeforeAll
    static void init() throws Exception {
        dataSource = new UnpooledDataSource("org.h2.Driver",
            "jdbc:h2:mem:flow_logic_delete_test;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE flow_node (id bigint primary key, node_code varchar(64),"
                + " node_name varchar(64), definition_id bigint, node_type int,"
                + " node_ratio varchar(20), create_time timestamp, update_time timestamp,"
                + " del_flag char(1) default '0', tenant_id varchar(40))");
        }

        // 逻辑删除工厂：默认配置，MP @TableLogic 生效
        logicSession = buildFactory(null).openSession(true);

        // 物理删除工厂：handler 在 mapper 注册前注册进 GlobalConfig
        physicalSession = buildFactory(new WarmFlowPostInitTableInfoHandler(false)).openSession(true);
    }

    /**
     * 构建独立 SqlSessionFactory；handler 非空时注册进 GlobalConfig（先于 mapper 注册）。
     */
    private static SqlSessionFactory buildFactory(WarmFlowPostInitTableInfoHandler handler) {
        GlobalConfig globalConfig = GlobalConfigUtils.defaults();
        if (handler != null) {
            globalConfig.setPostInitTableInfoHandler(handler);
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(FlowNodeMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @AfterEach
    void reset() {
        exec("DELETE FROM flow_node");
    }

    private static void exec(String sql) {
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void saveNode(long id) {
        FlowNode node = new FlowNode();
        node.setId(id);
        node.setNodeCode("n" + id);
        node.setNodeName("节点" + id);
        node.setNodeType(1);
        node.setDefinitionId(DEF_ID);
        node.setNodeRatio("0");
        physicalDao.save(node);
    }

    private static int rawCount(String where) {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM flow_node WHERE " + where)) {
            rs.next();
            return rs.getInt(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void logicDeleteModeKeepsLogicDelete() {
        saveNode(1);
        saveNode(2);
        logicDao.deleteNodeByDefIds(List.of(DEF_ID));
        // 逻辑删除：业务查询 0 行，del_flag='1' 尸体行保留 2 行
        assertEquals(0, logicDao.selectCount(new FlowNode()));
        assertEquals(2, rawCount("del_flag='1'"));
    }

    @Test
    void physicalModeDeletesPhysically() {
        saveNode(3);
        saveNode(4);
        physicalDao.deleteNodeByDefIds(List.of(DEF_ID));
        // 物理删除：物理行数归零，不产生 del_flag='1' 尸体行
        assertEquals(0, rawCount("1=1"));
        assertEquals(0, rawCount("del_flag='1'"));
    }

    @Test
    void physicalModeDeleteById() {
        saveNode(5);
        physicalDao.deleteById(5L);
        assertEquals(0, rawCount("1=1"));
    }

    @Test
    void physicalModeDeleteByEntity() {
        saveNode(6);
        saveNode(7);
        FlowNode cond = new FlowNode();
        cond.setDefinitionId(DEF_ID);
        physicalDao.delete(cond);
        assertEquals(0, rawCount("1=1"));
    }
}

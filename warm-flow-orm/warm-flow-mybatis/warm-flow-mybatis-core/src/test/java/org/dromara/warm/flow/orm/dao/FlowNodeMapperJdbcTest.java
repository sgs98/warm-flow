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

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.dromara.warm.flow.orm.entity.FlowNode;
import org.dromara.warm.flow.orm.mapper.FlowNodeMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 流程节点真实 SQL 验证（H2）：锁定 cooperate_type 的持久化、查询与更新语义。
 *
 * @author may
 */
class FlowNodeMapperJdbcTest {

    private static final String DDL = "CREATE TABLE flow_node ("
        + "id bigint primary key, node_type int, definition_id bigint, node_code varchar(100),"
        + "node_name varchar(100), permission_flag varchar(200), node_ratio varchar(200),"
        + "cooperate_type varchar(20), coordinate varchar(100), any_node_skip varchar(100),"
        + "listener_type varchar(100), listener_path varchar(400), form_custom char(1), form_path varchar(100),"
        + "create_time timestamp, update_time timestamp, create_by varchar(64), update_by varchar(64), ext clob,"
        + "node_config clob, event_type varchar(40), event_name varchar(100), attached_to_node_code varchar(100),"
        + "interrupting int, del_flag char(1) default '0', tenant_id varchar(40))";

    private static DataSource dataSource;
    private static SqlSession session;
    private static FlowNodeMapper mapper;

    @BeforeAll
    static void init() throws Exception {
        dataSource = new UnpooledDataSource("org.h2.Driver",
            "jdbc:h2:mem:flow_node_mybatis;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(DDL);
        }
        Configuration configuration = new Configuration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        try (InputStream input = Resources.getResourceAsStream("warm/flow/FlowNodeMapper.xml")) {
            new XMLMapperBuilder(input, configuration, "warm/flow/FlowNodeMapper.xml"
                , configuration.getSqlFragments()).parse();
        }
        session = new SqlSessionFactoryBuilder().build(configuration).openSession(true);
        mapper = session.getMapper(FlowNodeMapper.class);
    }

    @BeforeEach
    void cleanTable() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM flow_node");
        }
        session.clearCache();
    }

    @Test
    void groupCooperateType_roundTripsQueriesAndUpdates() {
        FlowNode node = new FlowNode().setId(2L).setNodeType(1).setDefinitionId(10L)
            .setNodeCode("group").setNodeRatio("0").setCooperateType("group").setDelFlag("0");
        assertEquals(1, mapper.insert(node));

        FlowNode loaded = mapper.selectById(2L, new FlowNode());
        assertEquals("group", loaded.getCooperateType());
        assertEquals(List.of(2L), mapper.selectList(new FlowNode().setCooperateType("group")
            , null, null, null).stream().map(FlowNode::getId).toList());

        assertEquals(1, mapper.updateById(new FlowNode().setId(2L).setCooperateType("sequence")));
        assertEquals("sequence", mapper.selectById(2L, new FlowNode()).getCooperateType());
    }

    @Test
    void cooperateType_roundTripsQueriesAndUpdates() {
        FlowNode node = new FlowNode().setId(1L).setNodeType(1).setDefinitionId(10L)
            .setNodeCode("audit").setNodeRatio("50").setCooperateType("sequence").setDelFlag("0");
        assertEquals(1, mapper.insert(node));

        List<FlowNode> nodes = mapper.selectList(new FlowNode().setCooperateType("sequence")
            , null, null, null);
        assertEquals(List.of(1L), nodes.stream().map(FlowNode::getId).toList());
        assertEquals("sequence", nodes.get(0).getCooperateType());

        assertEquals(1, mapper.updateById(new FlowNode().setId(1L).setCooperateType("parallel")));
        assertEquals("parallel", mapper.selectById(1L, new FlowNode()).getCooperateType());
    }
}

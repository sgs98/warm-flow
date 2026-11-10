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
import org.dromara.warm.flow.orm.entity.FlowUser;
import org.dromara.warm.flow.orm.mapper.FlowUserMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 流程办理人真实 SQL 验证（H2）：锁定 seq_no 的持久化、查询和条件删除语义。
 *
 * @author may
 */
class FlowUserMapperJdbcTest {

    private static final String DDL = "CREATE TABLE flow_user ("
        + "id bigint primary key, type char(1), processed_by varchar(80), associated bigint, seq_no int,"
        + "create_time timestamp, update_time timestamp, create_by varchar(80), update_by varchar(64),"
        + "del_flag char(1) default '0', tenant_id varchar(40))";

    private static DataSource dataSource;
    private static SqlSession session;
    private static FlowUserMapper mapper;

    @BeforeAll
    static void init() throws Exception {
        dataSource = new UnpooledDataSource("org.h2.Driver",
            "jdbc:h2:mem:flow_user_mybatis;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(DDL);
        }
        Configuration configuration = new Configuration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        try (InputStream input = Resources.getResourceAsStream("warm/flow/FlowUserMapper.xml")) {
            new XMLMapperBuilder(input, configuration, "warm/flow/FlowUserMapper.xml"
                , configuration.getSqlFragments()).parse();
        }
        session = new SqlSessionFactoryBuilder().build(configuration).openSession(true);
        mapper = session.getMapper(FlowUserMapper.class);
    }

    @BeforeEach
    void cleanTable() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM flow_user");
        }
        session.clearCache();
    }

    @Test
    void seqNo_roundTripsAndScopesPhysicalDelete() {
        insert(1L, 100L, "a", 0);
        insert(2L, 100L, "b", 1);

        List<FlowUser> users = mapper.selectList(new FlowUser().setAssociated(100L).setSeqNo(1)
            , null, null, null);
        assertEquals(List.of(2L), users.stream().map(FlowUser::getId).toList());
        assertEquals(1, users.get(0).getSeqNo());

        assertEquals(1, mapper.delete(new FlowUser().setAssociated(100L).setSeqNo(1)));
        assertEquals(1, count("SELECT COUNT(*) FROM flow_user WHERE associated = 100"));
        assertEquals(1, count("SELECT COUNT(*) FROM flow_user WHERE id = 1"));
    }

    @Test
    void groupMembersShareOneSeqNoAndRoundTrip() {
        insert(21L, 300L, "alice", 0);
        insert(22L, 300L, "bob", 0);
        insert(23L, 300L, "carol", 1);

        List<FlowUser> firstGroup = mapper.selectList(new FlowUser().setAssociated(300L).setSeqNo(0)
            , null, null, null);
        assertEquals(List.of(21L, 22L), firstGroup.stream().map(FlowUser::getId).sorted().toList());
        assertEquals(List.of("alice", "bob"), firstGroup.stream()
            .map(FlowUser::getProcessedBy).sorted().toList());
        assertEquals(1, mapper.selectList(new FlowUser().setAssociated(300L).setSeqNo(1)
            , null, null, null).size());
    }

    @Test
    void seqNo_scopesLogicDelete() {
        insert(11L, 200L, "a", 0);
        insert(12L, 200L, "b", 1);

        assertEquals(1, mapper.updateLogic(new FlowUser().setAssociated(200L).setSeqNo(1), "1", "0"));
        assertEquals(1, count("SELECT COUNT(*) FROM flow_user WHERE id = 11 AND del_flag = '0'"));
        assertEquals(1, count("SELECT COUNT(*) FROM flow_user WHERE id = 12 AND del_flag = '1'"));
    }

    private void insert(Long id, Long associated, String processedBy, Integer seqNo) {
        FlowUser user = new FlowUser().setId(id).setAssociated(associated).setProcessedBy(processedBy)
            .setType("1").setSeqNo(seqNo).setDelFlag("0");
        assertEquals(1, mapper.insert(user));
    }

    private int count(String sql) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

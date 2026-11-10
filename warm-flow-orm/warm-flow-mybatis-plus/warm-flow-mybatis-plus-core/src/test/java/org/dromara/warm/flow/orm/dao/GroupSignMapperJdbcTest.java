package org.dromara.warm.flow.orm.dao;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.dromara.warm.flow.orm.entity.FlowNode;
import org.dromara.warm.flow.orm.entity.FlowUser;
import org.dromara.warm.flow.orm.mapper.FlowNodeMapper;
import org.dromara.warm.flow.orm.mapper.FlowUserMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MyBatis-Plus 组签字段真实 SQL 验证（H2）：cooperate_type=group 往返，以及同组多人共享 seq_no。
 *
 * @author may
 */
class GroupSignMapperJdbcTest {

    private static final String NODE_DDL = "CREATE TABLE flow_node ("
        + "id bigint primary key, node_type int, definition_id bigint, node_code varchar(100),"
        + "node_name varchar(100), permission_flag varchar(200), node_ratio varchar(200),"
        + "cooperate_type varchar(20), coordinate varchar(100), any_node_skip varchar(100),"
        + "listener_type varchar(100), listener_path varchar(400), form_custom char(1), form_path varchar(100),"
        + "create_time timestamp, update_time timestamp, create_by varchar(64), update_by varchar(64), ext clob,"
        + "node_config clob, event_type varchar(40), event_name varchar(100), attached_to_node_code varchar(100),"
        + "interrupting int, del_flag char(1) default '0', tenant_id varchar(40))";

    private static final String USER_DDL = "CREATE TABLE flow_user ("
        + "id bigint primary key, type char(1), processed_by varchar(80), associated bigint, seq_no int,"
        + "create_time timestamp, update_time timestamp, create_by varchar(80), update_by varchar(64),"
        + "del_flag char(1) default '0', tenant_id varchar(40))";

    private static DataSource dataSource;
    private static SqlSession session;
    private static FlowNodeMapper nodeMapper;
    private static FlowUserMapper userMapper;

    @BeforeAll
    static void init() throws Exception {
        dataSource = new UnpooledDataSource("org.h2.Driver",
            "jdbc:h2:mem:group_sign_plus;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(NODE_DDL);
            statement.execute(USER_DDL);
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(FlowNodeMapper.class);
        configuration.addMapper(FlowUserMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true);
        nodeMapper = session.getMapper(FlowNodeMapper.class);
        userMapper = session.getMapper(FlowUserMapper.class);
    }

    @BeforeEach
    void cleanTable() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM flow_node");
            statement.execute("DELETE FROM flow_user");
        }
        session.clearCache();
    }

    @Test
    void groupCooperateTypeRoundTripsWithNodeConfig() {
        FlowNode node = new FlowNode().setId(1L).setNodeType(1).setDefinitionId(10L)
            .setNodeCode("group").setNodeRatio("0").setCooperateType("group")
            .setNodeConfig("{\"groupSign\":{\"groups\":[{\"id\":\"legal\",\"members\":[\"user:alice\"]}]}}")
            .setDelFlag("0");
        assertEquals(1, nodeMapper.insert(node));

        FlowNode loaded = nodeMapper.selectById(1L);
        assertEquals("group", loaded.getCooperateType());
        assertEquals("0", loaded.getNodeRatio());
        assertEquals(node.getNodeConfig(), loaded.getNodeConfig());

        assertEquals(1, nodeMapper.updateById(new FlowNode().setId(1L).setCooperateType("sequence")));
        assertEquals("sequence", nodeMapper.selectById(1L).getCooperateType());
    }

    @Test
    void groupMembersShareOneSeqNo() {
        insertUser(1L, "alice", 0);
        insertUser(2L, "bob", 0);
        insertUser(3L, "carol", 1);

        List<FlowUser> firstGroup = userMapper.selectList(new QueryWrapper<FlowUser>()
            .eq("associated", 200L).eq("seq_no", 0).orderByAsc("id"));
        assertEquals(List.of(1L, 2L), firstGroup.stream().map(FlowUser::getId).toList());
        assertEquals(List.of("alice", "bob"), firstGroup.stream().map(FlowUser::getProcessedBy).toList());
        assertEquals(1, userMapper.selectList(new QueryWrapper<FlowUser>()
            .eq("associated", 200L).eq("seq_no", 1)).size());
    }

    private void insertUser(Long id, String processedBy, Integer seqNo) {
        FlowUser user = new FlowUser().setId(id).setType("1").setProcessedBy(processedBy)
            .setAssociated(200L).setSeqNo(seqNo).setDelFlag("0");
        assertEquals(1, userMapper.insert(user));
    }
}

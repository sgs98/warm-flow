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

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.config.WarmFlow;
import org.dromara.warm.flow.core.constant.EventCons;
import org.dromara.warm.flow.core.dto.EventWaitQuery;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.utils.page.Page;
import org.dromara.warm.flow.orm.mapper.FlowEventWaitMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 事件等待记录查询的真实 SQL 验证（H2 + MyBatis-Plus）：验证条件 Wrapper 的列名、
 * 多值取「或」、白名单排序与分页插件组合可执行，条件口径与 MyBatis 适配层一致。
 *
 * @author may
 */
class EventWaitQueryJdbcTest {

    private static final String DDL = "CREATE TABLE flow_event_wait ("
        + " id bigint primary key, tenant_id varchar(40), del_flag char(1) default '0',"
        + " definition_id bigint, instance_id bigint, node_code varchar(100), event_type varchar(40),"
        + " event_name varchar(100), attached_task_id bigint, trigger_type varchar(40),"
        + " trigger_interval varchar(100), next_trigger_at timestamp, timezone varchar(40),"
        + " repeat_count int, max_repeat_count int, status varchar(40), retry_count int,"
        + " max_retry_count int, retry_at timestamp, lock_owner varchar(64), lock_until timestamp,"
        + " version int, payload varchar(4000), last_error varchar(2000),"
        + " create_time timestamp, update_time timestamp)";

    private static DataSource dataSource;
    private static SqlSession session;

    private static final FlowEventWaitDaoImpl dao = new FlowEventWaitDaoImpl() {
        @Override
        public FlowEventWaitMapper getMapper() {
            return session.getMapper(FlowEventWaitMapper.class);
        }
    };

    @BeforeAll
    static void init() throws Exception {
        dataSource = new UnpooledDataSource("org.h2.Driver",
            "jdbc:h2:mem:flow_event_wait_plus;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute(DDL);
        }
        FlowEngine.setFlowConfig(new WarmFlow());

        GlobalConfig globalConfig = GlobalConfigUtils.defaults();
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(FlowEventWaitMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true);
    }

    @BeforeEach
    void cleanTable() {
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute("DELETE FROM flow_event_wait");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        // 裸 JDBC 直写不经过 MyBatis-Plus，需显式清理会话一级缓存
        session.clearCache();
    }

    @Test
    void defaultQueryReturnsTimerFamilyOrderedByNextTriggerAt() {
        insertRow(1L, 100L, 1000L, "cooldown", "TIMER_CATCH", "WAITING", "0", "2026-01-01 02:00:00");
        insertRow(2L, 100L, 1000L, "cooldown", "TIMER_START", "WAITING", "0", "2026-01-01 01:00:00");
        insertRow(3L, 100L, 2000L, "confirm", "MESSAGE_CATCH", "WAITING", "0", null);
        insertRow(4L, 100L, 2000L, "confirm", "TIMER_BOUNDARY", "DEAD", "0", "2026-01-01 03:00:00");
        // 逻辑删除行不参与查询
        insertRow(5L, 100L, 3000L, "cooldown", "TIMER_CATCH", "WAITING", "1", "2026-01-01 01:00:00");

        assertEquals(3L, dao.selectCountByQuery(null));
        assertEquals(Arrays.asList(2L, 1L, 4L), ids(dao.selectListByQuery(null)));

        EventWaitQuery messageOnly = new EventWaitQuery();
        messageOnly.setEventTypes(List.of("MESSAGE_CATCH"));
        assertEquals(List.of(3L), ids(dao.selectListByQuery(messageOnly)));
    }

    @Test
    void conditionsAndPagingMatchCoreSemantics() {
        insertRow(11L, 100L, 1000L, "cooldown", "TIMER_CATCH", "WAITING", "0", "2026-01-01 01:00:00");
        insertRow(12L, 100L, 1000L, "cooldown", "TIMER_CATCH", "WAITING", "0", "2026-01-01 02:00:00");
        insertRow(13L, 100L, 2000L, "confirm", "TIMER_CATCH", "WAITING", "0", "2026-01-01 03:00:00");
        insertRow(14L, 100L, 2000L, "confirm", "TIMER_CATCH", "DEAD", "0", "2026-01-01 04:00:00");

        EventWaitQuery query = new EventWaitQuery();
        query.setInstanceId(1000L);
        query.setNodeCode("cooldown");
        query.setStatuses(List.of(EventCons.STATUS_WAITING));
        query.setNextTriggerAtFrom(date("2026-01-01 01:30:00"));
        query.setNextTriggerAtTo(date("2026-01-01 02:30:00"));
        assertEquals(1L, dao.selectCountByQuery(query));
        assertEquals(List.of(12L), ids(dao.selectListByQuery(query)));

        // 多值条件取「或」，不同条件之间取「且」
        EventWaitQuery statuses = new EventWaitQuery();
        statuses.setStatuses(List.of(EventCons.STATUS_WAITING, EventCons.STATUS_DEAD));
        assertEquals(4L, dao.selectCountByQuery(statuses));
        statuses.setInstanceIds(List.of(1000L));
        assertEquals(2L, dao.selectCountByQuery(statuses));

        // 分页：总数仍为匹配总数，仅返回当前页
        EventWaitQuery paged = new EventWaitQuery();
        paged.setPageSize(2);
        Page<EventWait> page = dao.selectPageByQuery(paged, new Page<>(paged.getPageNum(), paged.getPageSize()));
        assertEquals(4L, page.getTotal());
        assertEquals(Arrays.asList(11L, 12L), ids(page.getList()));

        EventWaitQuery secondPage = new EventWaitQuery();
        secondPage.setPageNum(2);
        secondPage.setPageSize(3);
        Page<EventWait> page2 = dao.selectPageByQuery(secondPage, new Page<>(2, 3));
        assertEquals(4L, page2.getTotal());
        assertEquals(List.of(14L), ids(page2.getList()));
        assertEquals(2, page2.getPageNum());
        assertEquals(3, page2.getPageSize());
    }

    @Test
    void messageClaimKeepsWinningPayload() {
        insertRow(31L, 100L, 1000L, "confirm", "MESSAGE_CATCH", "WAITING", "0", null);
        Date now = new Date();
        Date lockUntil = new Date(now.getTime() + 60_000L);
        assertTrue(dao.claimMessageWithPayload(31L, 0, "worker", now, lockUntil, "{\"id\":1}"));
        assertFalse(dao.claimMessageWithPayload(31L, 0, "worker", now, lockUntil, "{\"id\":2}"));
        assertEquals("{\"id\":1}", dao.selectById(31L).getPayload());
    }

    @Test
    void sqlServerStyleTimerLimitRuns() {
        insertRow(41L, 100L, 1000L, "timer", "TIMER_CATCH", "WAITING", "0", "2026-01-01 01:00:00");
        insertRow(42L, 100L, 1000L, "timer", "TIMER_CATCH", "WAITING", "0", "2026-01-01 02:00:00");
        FlowEngine.getFlowConfig().setDataSourceType("sqlserver");
        try {
            assertEquals(List.of(41L), ids(dao.selectWaitingDue(new Date(), null, null, 1)));
        } finally {
            FlowEngine.getFlowConfig().setDataSourceType(null);
        }
    }

    @Test
    void whitelistOrderAndEmptyResolutionMatchCoreSemantics() {
        insertRow(21L, 100L, 1000L, "cooldown", "TIMER_CATCH", "WAITING", "0", "2026-01-01 01:00:00");
        insertRow(22L, 100L, 1000L, "cooldown", "TIMER_CATCH", "WAITING", "0", "2026-01-01 02:00:00");

        EventWaitQuery descending = new EventWaitQuery();
        descending.setOrderBy("next_trigger_at");
        descending.setIsAsc("DESC");
        assertEquals(Arrays.asList(22L, 21L), ids(dao.selectListByQuery(descending)));

        EventWaitQuery byId = new EventWaitQuery();
        byId.setOrderBy("id");
        assertEquals(Arrays.asList(21L, 22L), ids(dao.selectListByQuery(byId)));

        // 解析不到实例集合时按空结果返回，而不是放弃条件
        EventWaitQuery noInstance = new EventWaitQuery();
        noInstance.setInstanceIds(List.of());
        noInstance.setInstanceId(9999L);
        assertEquals(0L, dao.selectCountByQuery(noInstance));
        assertTrue(dao.selectListByQuery(noInstance).isEmpty());
        assertTrue(dao.selectPageByQuery(noInstance, new Page<>(1, 10)).getList().isEmpty());
    }

    /**
     * 按 JDBC 直接插入一行，控制全部查询列。
     */
    private void insertRow(Long id, Long definitionId, Long instanceId, String nodeCode,
        String eventType, String status, String delFlag, String nextTriggerAt) {
        String sql = "INSERT INTO flow_event_wait (id, definition_id, instance_id, node_code, event_type,"
            + " status, del_flag, next_trigger_at, create_time, version)"
            + " VALUES (" + id + ", " + definitionId + ", " + instanceId + ", '" + nodeCode + "', '"
            + eventType + "', '" + status + "', '" + delFlag + "', "
            + (nextTriggerAt == null ? "null" : "TIMESTAMP '" + nextTriggerAt + "'")
            + ", TIMESTAMP '2026-01-01 00:00:00', 0)";
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        session.clearCache();
    }

    private List<Long> ids(List<EventWait> waits) {
        return waits.stream().map(EventWait::getId).collect(Collectors.toList());
    }

    private Date date(String value) {
        return new Date(Timestamp.valueOf(value).getTime());
    }

}

package org.dromara.warm.flow.orm.dao;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.dromara.warm.flow.orm.mapper.FlowInstanceMapper;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** MyBatis 实例行锁的真实 SQL、两连接等待与单行边界。 @author may */
class InstanceRowLockJdbcTest {
    @Test
    void secondWriterWaitsAndOtherInstanceRemainsIndependent() throws Exception {
        var ds = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:" + UUID.randomUUID()
            + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("create table flow_instance (id bigint primary key, update_time timestamp,"
                + " tenant_id varchar(30), del_flag char(1) default '0')");
            s.execute("insert into flow_instance(id, update_time) values (1, TIMESTAMP '2026-01-01 00:00:00'),"
                + " (2, TIMESTAMP '2026-01-01 00:00:00')");
        }
        org.dromara.warm.flow.core.FlowEngine.setNewIns(org.dromara.warm.flow.orm.entity.FlowInstance::new);
        org.dromara.warm.flow.core.FlowEngine.setFlowConfig(new org.dromara.warm.flow.core.config.WarmFlow());
        org.dromara.warm.flow.core.invoker.FrameInvoker.setBeanFunction(type -> null);
        org.dromara.warm.flow.core.FlowEngine.initTenantHandler(null);
        Configuration cfg = new Configuration();
        cfg.setEnvironment(new Environment("test", new JdbcTransactionFactory(), ds));
        try (var input = Resources.getResourceAsStream("warm/flow/FlowInstanceMapper.xml")) {
            new XMLMapperBuilder(input, cfg, "warm/flow/FlowInstanceMapper.xml", cfg.getSqlFragments()).parse();
        }
        var factory = new SqlSessionFactoryBuilder().build(cfg);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (SqlSession first = factory.openSession(false); SqlSession second = factory.openSession(false);
            SqlSession other = factory.openSession(false)) {
            InstanceRowLock.lock(first.getMapper(FlowInstanceMapper.class), 1L);
            InstanceRowLock.lock(other.getMapper(FlowInstanceMapper.class), 2L);
            other.commit();
            CountDownLatch entered = new CountDownLatch(1);
            Future<?> blocked = pool.submit(() -> {
                entered.countDown();
                InstanceRowLock.lock(second.getMapper(FlowInstanceMapper.class), 1L);
            });
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> blocked.get(200, TimeUnit.MILLISECONDS));
            first.commit(); blocked.get(5, TimeUnit.SECONDS); second.commit();
            try (var result = second.getConnection().createStatement().executeQuery("select update_time from flow_instance where id=1")) {
                assertTrue(result.next());
                assertEquals("2026-01-01 00:00:00.0", result.getTimestamp(1).toString());
            }
        } finally { pool.shutdownNow(); }
    }
}

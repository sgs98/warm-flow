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
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.dromara.warm.flow.orm.mapper.FlowInstanceMapper;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实例行锁的真实 SQL 与事务等待行为验证。
 *
 * @author may
 */
class InstanceRowLockJdbcTest {

    @Test
    void frameworkTenantPluginAndLogicalDeleteKeepInvisibleRowsUnlocked() throws Exception {
        var ds = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:" + java.util.UUID.randomUUID()
            + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("create table flow_instance (id bigint primary key, tenant_id varchar(30),"
                + " del_flag char(1) default '0', update_time timestamp)");
            s.execute("insert into flow_instance(id, tenant_id, del_flag) values (1,'B','0'),(2,'A','1')");
        }
        org.dromara.warm.flow.core.invoker.FrameInvoker.setBeanFunction(type -> null);
        org.dromara.warm.flow.core.FlowEngine.initTenantHandler(null);
        MybatisConfiguration cfg = new MybatisConfiguration();
        cfg.setEnvironment(new Environment("tenant-test", new JdbcTransactionFactory(), ds));
        var plugin = new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        plugin.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(
            new com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler() {
                @Override public net.sf.jsqlparser.expression.Expression getTenantId() {
                    return new net.sf.jsqlparser.expression.StringValue("A");
                }
            }));
        cfg.addInterceptor(plugin);
        cfg.addMapper(FlowInstanceMapper.class);
        var factory = new MybatisSqlSessionFactoryBuilder().build(cfg);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (SqlSession first = factory.openSession(false)) {
            FlowInstanceMapper mapper = first.getMapper(FlowInstanceMapper.class);
            for (Long id : java.util.List.of(1L, 2L)) {
                org.junit.jupiter.api.Assertions.assertEquals(0L, mapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<org.dromara.warm.flow.orm.entity.FlowInstance>()
                        .eq("id", id)).longValue());
                assertThrows(org.dromara.warm.flow.core.exception.FlowException.class,
                    () -> InstanceRowLock.lock(mapper, id));
                Future<Integer> other = pool.submit(() -> {
                    try (Connection c = ds.getConnection(); var s = c.prepareStatement(
                        "update flow_instance set update_time=update_time where id=?")) {
                        s.setLong(1, id); return s.executeUpdate();
                    }
                });
                org.junit.jupiter.api.Assertions.assertEquals(1, other.get(1, TimeUnit.SECONDS).intValue());
            }
            first.commit();
        } finally { pool.shutdownNow(); }
    }

    @Test
    void secondWriterWaitsForFirstTransaction() throws Exception {
        DataSource dataSource = new UnpooledDataSource("org.h2.Driver",
            "jdbc:h2:mem:instance_row_lock;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE flow_instance (id bigint primary key,"
                + " del_flag char(1) default '0', update_time timestamp)");
            statement.execute("INSERT INTO flow_instance (id, del_flag) VALUES (1, '0')");
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(FlowInstanceMapper.class);
        SqlSessionFactory factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (SqlSession first = factory.openSession(false); SqlSession second = factory.openSession(false)) {
            InstanceRowLock.lock(first.getMapper(FlowInstanceMapper.class), 1L);
            CountDownLatch started = new CountDownLatch(1);
            Future<?> blocked = executor.submit(() -> {
                started.countDown();
                InstanceRowLock.lock(second.getMapper(FlowInstanceMapper.class), 1L);
            });

            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> blocked.get(200, TimeUnit.MILLISECONDS));
            first.commit();
            blocked.get(5, TimeUnit.SECONDS);
            second.commit();
        } finally {
            executor.shutdownNow();
        }
    }
}

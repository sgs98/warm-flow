package org.dromara.warm.flow.spring.boot.config;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.service.task.RejectConfig;
import org.dromara.warm.flow.orm.dao.InstanceRowLock;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

/**
 * 三个内置写服务共用的同实例原子接入；空事务只允许旧重走模式。 @author may
 */
final class InstanceTransactionSupport {
    private final TransactionTemplate template;

    InstanceTransactionSupport(TransactionTemplate template) {
        this.template = template == null ? null : new TransactionTemplate(template.getTransactionManager(), template);
        if (this.template != null) {
            this.template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        }
        if (this.template != null && this.template.getTransactionManager() instanceof DataSourceTransactionManager) {
            // 只设置本服务新建事务；REQUIRED 外层隔离级别不被覆盖，宿主 template 不被修改。
            this.template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        }
    }

    <T> T execute(Long instanceId, Supplier<T> work) {
        if (template == null) {
            requireInstance(instanceId);
            return work.get();
        }
        return template.execute(status -> {
            // MySQL RR 的普通读视图不能由 UPDATE 锁刷新；在任何写/锁前拒绝直返。
            if (!readCommitted()) requireInstance(instanceId);
            InstanceRowLock.lock(instanceId);
            return work.get();
        });
    }

    private void requireInstance(Long instanceId) {
        Instance instance = instanceId == null ? null : FlowEngine.insService().getById(instanceId);
        // 静态设计规则始终保留在实例快照，门禁不是对 pending 当前状态作流转决策。
        if (instance != null) requireSupported(instance.getDefJson());
    }

    void requireStart(String flowCode, Long definitionId) {
        if (template != null && readCommitted()) return;
        Definition selected = definitionId == null ? FlowEngine.defService().getPublishByFlowCode(flowCode)
            : FlowEngine.defService().getById(definitionId);
        if (selected != null) {
            Definition definition = FlowEngine.defService().getAllDataDefinition(selected.getId());
            requireSupported(FlowEngine.jsonConvert.objToStr(DefJson.copyDef(definition)));
        }
    }

    private void requireSupported(String snapshot) {
        if (!RejectConfig.requiresAtomicExecution(snapshot)) return;
        if (template == null) {
            throw new FlowException("驳回直返需要事务管理器和同实例行锁，请配置 PlatformTransactionManager");
        }
        throw new FlowException("驳回直返需要 READ_COMMITTED 事务；请将外层事务隔离级别设为 READ_COMMITTED");
    }

    private boolean readCommitted() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return template.getIsolationLevel() == TransactionDefinition.ISOLATION_READ_COMMITTED;
        }
        Integer declared = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        if (declared != null) return declared == Connection.TRANSACTION_READ_COMMITTED;
        // DEFAULT 外层事务没有 Spring 隔离级别元数据，必须检查已绑定的实际 JDBC 连接。
        if (template.getTransactionManager() instanceof DataSourceTransactionManager manager) {
            var source = manager.getDataSource();
            if (source == null) return false;
            Connection connection = DataSourceUtils.getConnection(source);
            try {
                return connection.getTransactionIsolation() == Connection.TRANSACTION_READ_COMMITTED;
            } catch (SQLException e) {
                throw new FlowException("无法确认流程事务隔离级别: " + e.getMessage());
            } finally {
                DataSourceUtils.releaseConnection(connection, source);
            }
        }
        return false;
    }
}

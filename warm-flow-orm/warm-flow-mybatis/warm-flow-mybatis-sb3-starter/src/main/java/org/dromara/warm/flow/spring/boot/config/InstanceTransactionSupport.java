package org.dromara.warm.flow.spring.boot.config;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.service.task.RejectConfig;
import org.dromara.warm.flow.orm.dao.InstanceRowLock;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * 保留宿主隔离级别的同实例事务与当前读接入。 @author may
 */
final class InstanceTransactionSupport {
    private final TransactionTemplate template;

    InstanceTransactionSupport(TransactionTemplate template) {
        this.template = template == null ? null : new TransactionTemplate(template.getTransactionManager(), template);
        if (this.template != null) {
            this.template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        }
    }

    <T> T execute(Long instanceId, Supplier<T> work) {
        if (template == null) {
            // 无事务管理器：普通办理照常放行，实际直返由 core 明确拒绝，不改变路由。
            boolean previous = RejectConfig.isAtomicExecutionSupported();
            RejectConfig.setAtomicExecutionSupported(false);
            try {
                requirePendingSupported(instanceId);
                return work.get();
            } finally {
                RejectConfig.setAtomicExecutionSupported(previous);
            }
        }
        return template.execute(status -> {
            boolean previous = RejectConfig.isAtomicExecutionSupported();
            RejectConfig.setAtomicExecutionSupported(true);
            try {
                return InstanceRowLock.withLock(instanceId, work);
            } finally {
                RejectConfig.setAtomicExecutionSupported(previous);
            }
        });
    }

    /**
     * 已存在直返关联时必须具备事务管理器，不检查宿主选择的隔离级别。
     *
     * @param instanceId 流程实例主键
     */
    private void requirePendingSupported(Long instanceId) {
        Instance instance = instanceId == null ? null : FlowEngine.insService().getById(instanceId);
        if (instance == null || !String.valueOf(instance.getVariable()).contains(RejectConfig.FLOW_VARIABLE_KEY)) {
            return;
        }
        throw new FlowException("驳回直返需要事务管理器和同实例行锁，请配置 PlatformTransactionManager");
    }
}

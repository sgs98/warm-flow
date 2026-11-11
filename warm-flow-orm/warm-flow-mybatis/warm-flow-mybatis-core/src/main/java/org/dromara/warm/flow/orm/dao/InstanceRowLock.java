package org.dromara.warm.flow.orm.dao;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.invoker.FrameInvoker;
import org.dromara.warm.flow.core.utils.AssertUtil;
import org.dromara.warm.flow.orm.entity.FlowInstance;
import org.dromara.warm.flow.orm.utils.TenantDeleteUtil;
import org.dromara.warm.flow.orm.mapper.FlowInstanceMapper;

/** 用单行无值变化 UPDATE 取得事务行锁，避免引入四库 FOR UPDATE 方言。 @author may */
public final class InstanceRowLock {
    private InstanceRowLock() { }

    public static void lock(Long instanceId) {
        if (instanceId == null) { return; }
        lock(FrameInvoker.getBean(FlowInstanceMapper.class), instanceId);
    }

    static void lock(FlowInstanceMapper mapper, Long instanceId) {
        FlowInstance boundary = (FlowInstance) TenantDeleteUtil.getEntity(FlowEngine.newIns());
        int changed = mapper.lockInstance(instanceId, boundary);
        // MySQL useAffectedRows=true 对已锁定但未改值的行返回 0；只在锁后按相同边界确认可见性。
        AssertUtil.isFalse(changed > 0 || mapper.selectById(instanceId, boundary) != null,
            ExceptionCons.NOT_FOUNT_INSTANCE);
    }
}

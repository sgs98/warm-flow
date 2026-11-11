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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.invoker.FrameInvoker;
import org.dromara.warm.flow.core.utils.AssertUtil;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.dromara.warm.flow.orm.entity.FlowInstance;
import org.dromara.warm.flow.orm.mapper.FlowInstanceMapper;

/**
 * 在流程写事务中锁定实例行，使任务快照与事件到达检查串行化。
 *
 * @author may
 */
public final class InstanceRowLock {

    private InstanceRowLock() {
    }

    public static void lock(Long instanceId) {
        if (instanceId == null) {
            return;
        }
        lock(FrameInvoker.getBean(FlowInstanceMapper.class), instanceId);
    }

    static void lock(FlowInstanceMapper mapper, Long instanceId) {
        // 条件 UPDATE 在各支持数据库中取得实例行写锁；不依赖 SELECT FOR UPDATE 方言。
        String tenant = FlowEngine.tenantHandler() == null ? null : FlowEngine.tenantHandler().getTenantId();
        // 引擎租户直接入谓词；框架租户插件和 MP 原生逻辑删除仍由 mapper 叠加。
        int changed = mapper.update(null,
            new LambdaUpdateWrapper<FlowInstance>()
                .eq(FlowInstance::getId, instanceId)
                .eq(StringUtils.isNotEmpty(tenant), FlowInstance::getTenantId, tenant)
                .setSql("update_time = update_time"));
        // 无值变化 UPDATE 的 changed rows 可为 0；锁后确认同一可见边界，不作锁前 SELECT。
        AssertUtil.isFalse(changed > 0 || mapper.selectCount(new LambdaQueryWrapper<>(FlowInstance.class)
            .eq(FlowInstance::getId, instanceId)
            .eq(StringUtils.isNotEmpty(tenant), FlowInstance::getTenantId, tenant)) > 0,
            ExceptionCons.NOT_FOUNT_INSTANCE);
    }
}

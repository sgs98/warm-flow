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

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.dromara.warm.flow.core.invoker.FrameInvoker;
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
        mapper.update(null,
            new LambdaUpdateWrapper<FlowInstance>()
                .eq(FlowInstance::getId, instanceId)
                .setSql("update_time = update_time"));
    }
}

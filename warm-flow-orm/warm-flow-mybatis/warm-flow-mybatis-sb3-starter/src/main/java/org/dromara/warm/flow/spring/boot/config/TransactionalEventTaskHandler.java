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
package org.dromara.warm.flow.spring.boot.config;

import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.timer.EventTaskHandler;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 带事务边界的事件任务处理器：把一次事件推进（改任务 / 实例 / 等待记录）
 * 收进同一个数据库事务，任一步失败整体回滚，不产生「任务已归档但没有等待记录」
 * 的半状态。
 * <p>
 * 事务传播取默认 {@code REQUIRED}；消息和错误事件触发前拒绝外围事务，
 * 保证 CAS 抢占及失败记账发生在独立的推进事务之外。
 *
 * @author may
 */
public class TransactionalEventTaskHandler implements EventTaskHandler {

    @Override
    public void checkTriggerTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("消息或错误事件触发不能在外围事务中执行");
        }
    }

    /**
     * 被包装的真实处理器（事件运行时推进）。
     */
    private final EventTaskHandler delegate;

    /**
     * 事务模板。
     */
    private final TransactionTemplate transactionTemplate;

    /**
     * @param delegate           被包装的处理器
     * @param transactionTemplate 事务模板
     */
    public TransactionalEventTaskHandler(EventTaskHandler delegate, TransactionTemplate transactionTemplate) {
        this.delegate = delegate;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void handle(EventWait wait) {
        transactionTemplate.executeWithoutResult(status -> delegate.handle(wait));
    }

    @Override
    public void handle(EventWait wait, WorkflowContext context) {
        transactionTemplate.executeWithoutResult(status -> delegate.handle(wait, context));
    }

    @Override
    public void handle(EventWait wait, WorkflowContext context, Runnable completion) {
        transactionTemplate.executeWithoutResult(status -> {
            delegate.handle(wait, context);
            completion.run();
        });
    }
}

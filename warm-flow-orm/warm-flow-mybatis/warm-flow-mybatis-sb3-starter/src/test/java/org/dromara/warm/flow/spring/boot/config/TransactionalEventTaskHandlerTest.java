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

import org.dromara.warm.flow.core.timer.EventTaskHandler;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 事件推进与完成回调的事务边界测试。
 *
 * @author may
 */
class TransactionalEventTaskHandlerTest {

    @Test
    void rejectsOuterTransactionBeforeMessageClaim() {
        PlatformTransactionManager manager = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
        TransactionalEventTaskHandler handler =
            new TransactionalEventTaskHandler(wait -> { }, new TransactionTemplate(manager));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(IllegalStateException.class, handler::checkTriggerTransaction);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void completionRunsBeforeCommitAndFailureRollsBack() {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger commits = new AtomicInteger();
        AtomicInteger rollbacks = new AtomicInteger();
        PlatformTransactionManager manager = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                active.incrementAndGet();
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
                active.decrementAndGet();
                commits.incrementAndGet();
            }

            @Override
            public void rollback(TransactionStatus status) {
                active.decrementAndGet();
                rollbacks.incrementAndGet();
            }
        };
        EventTaskHandler delegate = wait -> assertEquals(1, active.get());
        TransactionalEventTaskHandler handler =
            new TransactionalEventTaskHandler(delegate, new TransactionTemplate(manager));

        handler.handle(null, null, () -> assertEquals(1, active.get()));
        assertEquals(1, commits.get());
        assertThrows(IllegalStateException.class, () -> handler.handle(null, null, () -> {
            assertEquals(1, active.get());
            throw new IllegalStateException("completion failed");
        }));
        assertEquals(1, commits.get());
        assertEquals(1, rollbacks.get());
        assertEquals(0, active.get());
    }
}

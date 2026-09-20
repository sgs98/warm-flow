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
package org.dromara.warm.flow.core.utils;

import org.dromara.warm.flow.core.invoker.FrameInvoker;
import org.dromara.warm.flow.core.utils.page.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 核心公共工具 API 的直接覆盖测试。
 *
 * @author warm
 */
class CoreUtilityApiTest {

    @AfterEach
    void resetIdGenerator() {
        IdUtils.setInstanceNative(null);
    }

    @Test
    void frameInvoker_page_andMapUtilities_keepPublicContracts() {
        FrameInvoker.setBeanFunction(type -> type.getName().equals(String.class.getName()) ? "bean" : null);
        FrameInvoker.setCfgFunction(key -> "cfg:" + key);
        assertEquals("bean", FrameInvoker.getBean(String.class));
        assertEquals("cfg:test", FrameInvoker.getCfg("test"));
        FrameInvoker.setBeanFunction(type -> null);
        FrameInvoker.setCfgFunction(key -> null);

        Page<String> empty = Page.empty();
        assertEquals(0, empty.getTotal());
        assertEquals(2, Page.<String>pageOf(2, 20).getPageNum());
        assertEquals(20, Page.<String>pageOf(2, 20).getPageSize());
        assertEquals("id", empty.setOrderBy("id").getOrderBy());
        assertEquals("DESC", empty.setIsAsc("DESC").getIsAsc());

        Map<String, Object> merged = MapUtil.mergeAll(Map.of("a", 1), Map.of("a", 2, "b", 3));
        assertEquals(2, merged.get("a"));
        assertEquals(3, merged.get("b"));
        assertTrue(MapUtil.isNotEmpty(merged));
        assertFalse(MapUtil.isEmpty(merged));
        assertEquals(List.of("a", "b"), List.copyOf(merged.keySet()));
    }

    @Test
    void expressionListenerAndSqlHelpers_coverNullAndBoundaryContracts() {
        assertTrue(SqlHelper.retBool(1));
        assertFalse(SqlHelper.retBool(0));
        assertTrue(SqlHelper.retBool(1L));
        assertEquals(0L, SqlHelper.retCount(null));
        assertEquals(3L, SqlHelper.retCount(3L));
        assertEquals("fallback", StringUtils.emptyDefault("", "fallback"));
        assertEquals("value", StringUtils.emptyDefault("value", "fallback"));
        assertEquals("true", String.valueOf(ObjectUtil.defaultNull(null, "true")));
        assertEquals("x", ObjectUtil.defaultNull("x", "y"));
        Long firstId = IdUtils.nextId();
        Long secondId = IdUtils.nextId();
        assertNotNull(firstId);
        assertNotEquals(firstId, secondId);
        assertNotNull(IdUtils.nextIdStr());
    }

    @Test
    void idUtils_prefersRegisteredNativeGenerator() {
        LongSupplier nativeGenerator = () -> 123L;
        IdUtils.setInstanceNative(nativeGenerator);
        assertEquals(123L, IdUtils.nextId());
        assertEquals("123", IdUtils.nextIdStr());
    }

    @Test
    void idUtils_switchesToNativeGeneratorAfterDefaultGeneratorWasUsed() {
        assertNotNull(IdUtils.nextId());

        IdUtils.setInstanceNative(() -> 456L);
        assertEquals(456L, IdUtils.nextId());
    }

    @Test
    void idUtils_rejectsClockRollback() throws ReflectiveOperationException {
        var lastTimestampField = IdUtils.class.getDeclaredField("lastTimestamp");
        lastTimestampField.setAccessible(true);
        long lastTimestamp = lastTimestampField.getLong(null);
        try {
            lastTimestampField.setLong(null, Long.MAX_VALUE);
            assertThrows(IllegalStateException.class, IdUtils::nextId);
        } finally {
            lastTimestampField.setLong(null, lastTimestamp);
        }
    }
}

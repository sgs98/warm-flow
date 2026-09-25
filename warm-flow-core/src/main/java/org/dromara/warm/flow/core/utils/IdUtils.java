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

import java.util.function.LongSupplier;

/**
 * 唯一id
 *
 * @author warm
 * @since 2023/5/17 23:08
 */
public class IdUtils {

    /**
     * orm框架配置了原生id算法
     */
    private static volatile LongSupplier instanceNative;

    /**
     * 雪花算法的起始时间戳（保持与原有 19 位算法一致）
     */
    private static final long TWEPOCH = 1420041600000L;

    private static final long MAX_WORKER_ID = 31L;

    private static final long MAX_DATACENTER_ID = 31L;

    private static final long WORKER_ID_SHIFT = 12L;

    private static final long DATACENTER_ID_SHIFT = 17L;

    private static final long TIMESTAMP_LEFT_SHIFT = 22L;

    private static final long SEQUENCE_MASK = 4095L;

    private static long workerId;

    private static long datacenterId;

    private static long sequence;

    private static long lastTimestamp = -1L;

    private static boolean initialized;

    public static String nextIdStr() {
        return nextId().toString();
    }

    public static Long nextId() {
        return nextId(1, 1);
    }

    public static Long nextId(long workerId, long datacenterId) {
        LongSupplier nativeInstance = instanceNative;
        if (nativeInstance != null) {
            return nativeInstance.getAsLong();
        }

        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(String.format("worker Id can't be greater than %d or less than 0", MAX_WORKER_ID));
        }
        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException(String.format("datacenter Id can't be greater than %d or less than 0", MAX_DATACENTER_ID));
        }

        synchronized (IdUtils.class) {
            if (!initialized) {
                IdUtils.workerId = workerId;
                IdUtils.datacenterId = datacenterId;
                initialized = true;
            }

            long timestamp = System.currentTimeMillis();
            if (timestamp < lastTimestamp) {
                throw new IllegalStateException(
                    String.format("Clock moved backwards. Refusing to generate id for %d milliseconds", lastTimestamp - timestamp));
            }
            if (lastTimestamp == timestamp) {
                sequence = (sequence + 1) & SEQUENCE_MASK;
                if (sequence == 0) {
                    timestamp = tilNextMillis(lastTimestamp);
                }
            } else {
                sequence = 0L;
            }

            lastTimestamp = timestamp;
            return ((timestamp - TWEPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (IdUtils.datacenterId << DATACENTER_ID_SHIFT)
                | (IdUtils.workerId << WORKER_ID_SHIFT)
                | sequence;
        }
    }

    private static long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    public static void setInstanceNative(LongSupplier instanceNative) {
        IdUtils.instanceNative = instanceNative;
    }

}

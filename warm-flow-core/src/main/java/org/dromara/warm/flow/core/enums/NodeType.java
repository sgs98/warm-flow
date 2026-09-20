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
package org.dromara.warm.flow.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.dromara.warm.flow.core.utils.ObjectUtil;

/**
 * 流程节点类型。
 * <p>
 * key 持久化到节点、任务和实例中，value 用于设计器或接口展示。
 *
 * @author warm
 * @since 2023/3/31 12:16
 */
@AllArgsConstructor
@Getter
public enum NodeType {

    /**
     * 开始节点
     */
    START(0, "start"),

    /**
     * 中间节点
     */
    BETWEEN(1, "between"),

    /**
     * 结束节点
     */
    END(2, "end"),

    /**
     * 互斥网关
     */
    SERIAL(3, "serial"),

    /**
     * 并行网关
     */
    PARALLEL(4, "parallel"),

    /**
     * 包容网关
     */
    INCLUSIVE(5, "inclusive");

    private final Integer key;
    private final String value;

    /**
     * 根据展示值获取节点类型 key。
     *
     * @param value 展示值
     * @return 节点类型 key；未匹配时返回 {@code null}
     */
    public static Integer getKeyByValue(String value) {
        for (NodeType item : NodeType.values()) {
            if (item.getValue().equals(value)) {
                return item.getKey();
            }
        }
        return null;
    }

    /**
     * 根据节点类型 key 获取展示值。
     *
     * @param key 节点类型 key
     * @return 展示值；未匹配时返回 {@code null}
     */
    public static String getValueByKey(Integer key) {
        for (NodeType item : NodeType.values()) {
            if (item.getKey().equals(key)) {
                return item.getValue();
            }
        }
        return null;
    }

    /**
     * 根据节点类型 key 获取枚举。
     *
     * @param key 节点类型 key
     * @return 节点类型枚举；未匹配时返回 {@code null}
     */
    public static NodeType getByKey(Integer key) {
        for (NodeType item : NodeType.values()) {
            if (item.getKey().equals(key)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 判断是否开始节点
     *
     * @param key 枚举key
     * @return 是开始节点时返回 {@code true}
     */
    public static Boolean isStart(Integer key) {
        return ObjectUtil.isNotNull(key) && (NodeType.START.getKey().equals(key));
    }

    /**
     * 判断是否中间节点
     *
     * @param key 枚举key
     * @return 是中间节点时返回 {@code true}
     */
    public static Boolean isBetween(Integer key) {
        return ObjectUtil.isNotNull(key) && (NodeType.BETWEEN.getKey().equals(key));
    }

    /**
     * 判断是否结束节点
     *
     * @param key 枚举key
     * @return 是结束节点时返回 {@code true}
     */
    public static Boolean isEnd(Integer key) {
        return ObjectUtil.isNotNull(key) && (NodeType.END.getKey().equals(key));
    }

    /**
     * 判断是否网关节点
     *
     * @param key 枚举key
     * @return 是任一网关节点时返回 {@code true}
     */
    public static Boolean isGateWay(Integer key) {
        return ObjectUtil.isNotNull(key) && (NodeType.SERIAL.getKey().equals(key)
            || NodeType.PARALLEL.getKey().equals(key) || NodeType.INCLUSIVE.getKey().equals(key));
    }

    /**
     * 判断是否互斥网关节点
     *
     * @param key 枚举key
     * @return 是互斥网关时返回 {@code true}
     */
    public static Boolean isGateWaySerial(Integer key) {
        return ObjectUtil.isNotNull(key) && NodeType.SERIAL.getKey().equals(key);
    }

    /**
     * 判断是否并行网关节点
     *
     * @param key 枚举key
     * @return 是并行网关时返回 {@code true}
     */
    public static Boolean isGateWayParallel(Integer key) {
        return ObjectUtil.isNotNull(key) && NodeType.PARALLEL.getKey().equals(key);
    }

    /**
     * 判断是否包容网关节点
     *
     * @param key 枚举key
     * @return 是包容网关时返回 {@code true}
     */
    public static Boolean isGateWayInclusive(Integer key) {
        return ObjectUtil.isNotNull(key) && NodeType.INCLUSIVE.getKey().equals(key);
    }

}

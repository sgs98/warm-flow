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
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.ObjectUtil;
import org.dromara.warm.flow.core.utils.StringUtils;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程图节点和连线展示状态。
 * <p>
 * key 用于保存节点/连线办理状态，color 用于流程图渲染。颜色支持全局配置，
 * 也支持经典模型和仿钉钉模型分别覆盖。
 *
 * @author warm
 * @since 2023/3/31 12:16
 */
@Getter
@AllArgsConstructor
public enum ChartStatus {

    NOT_DONE(0, "未办理", new Color(107, 114, 128)),

    TO_DO(1, "待办理", new Color(245, 158, 11)),

    DONE(2, "已办理", new Color(56, 161, 105));

    private final Integer key;
    private final String value;
    private final Color color;

    private static final Map<Integer, Color> CUSTOM_COLOR = new HashMap<>();
    private static final Map<Integer, Color> CUSTOM_COLOR_CLASSICS = new HashMap<>();
    private static final Map<Integer, Color> CUSTOM_COLOR_MIMIC = new HashMap<>();

    /**
     * 初始化流程图状态自定义颜色。
     * <p>
     * 颜色配置按未办理、待办理、已办理顺序传入，每个颜色为 {@code r,g,b} 格式。
     *
     * @param chartStatusColor 通用颜色配置
     * @param chartStatusColorClassics 经典模型颜色配置
     * @param chartStatusColorMimic 仿钉钉模型颜色配置
     */
    public static void initCustomColor(List<String> chartStatusColor, List<String> chartStatusColorClassics,
                                       List<String> chartStatusColorMimic) {
        if (CollUtil.isNotEmpty(chartStatusColor) && chartStatusColor.size() == 3) {
            for (int i = 0; i < chartStatusColor.size(); i++) {
                String statusColor = chartStatusColor.get(i);
                if (StringUtils.isNotEmpty(statusColor)) {
                    String[] colorArr = statusColor.split(",");
                    if (colorArr.length == 3) {
                        ChartStatus.CUSTOM_COLOR.put(i, new Color(Integer.parseInt(colorArr[0]), Integer.parseInt(colorArr[1]), Integer.parseInt(colorArr[2])));
                    }
                }
            }
        }
        if (CollUtil.isNotEmpty(chartStatusColorClassics) && chartStatusColorClassics.size() == 3) {
            for (int i = 0; i < chartStatusColorClassics.size(); i++) {
                String statusColor = chartStatusColorClassics.get(i);
                if (StringUtils.isNotEmpty(statusColor)) {
                    String[] colorArr = statusColor.split(",");
                    if (colorArr.length == 3) {
                        ChartStatus.CUSTOM_COLOR_CLASSICS.put(i, new Color(Integer.parseInt(colorArr[0]), Integer.parseInt(colorArr[1]), Integer.parseInt(colorArr[2])));
                    }
                }
            }
        }
        if (CollUtil.isNotEmpty(chartStatusColorMimic) && chartStatusColorMimic.size() == 3) {
            for (int i = 0; i < chartStatusColorMimic.size(); i++) {
                String statusColor = chartStatusColorMimic.get(i);
                if (StringUtils.isNotEmpty(statusColor)) {
                    String[] colorArr = statusColor.split(",");
                    if (colorArr.length == 3) {
                        ChartStatus.CUSTOM_COLOR_MIMIC.put(i, new Color(Integer.parseInt(colorArr[0]), Integer.parseInt(colorArr[1]), Integer.parseInt(colorArr[2])));
                    }
                }
            }
        }
    }

    /**
     * 获取未办理状态颜色。
     *
     * @param modelValue 设计器模型
     * @return 颜色
     */
    public static Color getNotDone(String modelValue) {
        return getColorByKey(ChartStatus.NOT_DONE, modelValue);
    }

    /**
     * 获取待办理状态颜色。
     *
     * @param modelValue 设计器模型
     * @return 颜色
     */
    public static Color getToDo(String modelValue) {
        return getColorByKey(ChartStatus.TO_DO, modelValue);
    }

    /**
     * 获取已办理状态颜色。
     *
     * @param modelValue 设计器模型
     * @return 颜色
     */
    public static Color getDone(String modelValue) {
        return getColorByKey(ChartStatus.DONE, modelValue);
    }

    /**
     * 按状态和设计器模型获取颜色，优先使用模型专属配置，其次使用通用配置，最后使用枚举默认颜色。
     *
     * @param chartStatus 流程图状态
     * @param modelValue 设计器模型
     * @return 颜色
     */
    public static Color getColorByKey(ChartStatus chartStatus, String modelValue) {
        Color color = null;
        if (ModelEnum.CLASSICS.name().equals(modelValue)) {
            color = ChartStatus.CUSTOM_COLOR_CLASSICS.get(chartStatus.getKey());
        } else if (ModelEnum.MIMIC.name().equals(modelValue)) {
            color = ChartStatus.CUSTOM_COLOR_MIMIC.get(chartStatus.getKey());
        }
        if (ObjectUtil.isNull(color)) {
            color = ChartStatus.CUSTOM_COLOR.get(chartStatus.getKey());
        }
        return ObjectUtil.defaultNull(color, chartStatus.getColor());
    }

    /**
     * 按状态 key 获取颜色。
     *
     * @param key 状态 key
     * @return 颜色；未匹配状态时返回 {@code null}
     */
    public static Color getColorByKey(Integer key) {
        for (ChartStatus item : ChartStatus.values()) {
            if (item.getKey().equals(key)) {
                Color color = ChartStatus.CUSTOM_COLOR.get(key);
                return ObjectUtil.defaultNull(color, item.getColor());
            }
        }
        return null;
    }

    /**
     * 判断是否未办理
     *
     * @param key 状态
     * @return 是未办理状态时返回 {@code true}
     */
    public static Boolean isNotDone(Integer key) {
        return ObjectUtil.isNotNull(key) && (ChartStatus.NOT_DONE.getKey().equals(key));
    }

    /**
     * 判断是否待办理
     *
     * @param key 状态
     * @return 是待办理状态时返回 {@code true}
     */
    public static Boolean isToDo(Integer key) {
        return ObjectUtil.isNotNull(key) && (ChartStatus.TO_DO.getKey().equals(key));
    }

    /**
     * 判断是否已办理
     *
     * @param key 状态
     * @return 是已办理状态时返回 {@code true}
     */
    public static Boolean isDone(Integer key) {
        return ObjectUtil.isNotNull(key) && (ChartStatus.DONE.getKey().equals(key));
    }

}

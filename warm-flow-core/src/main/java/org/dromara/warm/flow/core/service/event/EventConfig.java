package org.dromara.warm.flow.core.service.event;

import lombok.Getter;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.utils.AssertUtil;
import org.dromara.warm.flow.core.utils.MapUtil;
import org.dromara.warm.flow.core.utils.StringUtils;

import java.util.Map;

/**
 * 事件节点配置：解析并校验 node_config.event 段。
 * <p>
 * 只读 {@code node_config.event} 段，与 {@code ServiceTaskConfig} 只读 {@code serviceTask} 段互不越界
 * （节点类型 {@code SERVICE} 与事件节点 {@code BETWEEN} 互斥，同一节点的 node_config 不会同时出现两段）。
 * 消息事件的 event 段可以为空；定时事件必须给出 timerType 与 timerValue。
 * 节点默认流程变量不属于事件专用配置，统一由 {@code NodeDefaults} 读取 {@code node_config.variables}。
 *
 * @author may
 */
@Getter
public class EventConfig {

    /**
     * 延迟定时器：基准时间 + 时长，例如 {@code P3D}、{@code PT30M}。
     */
    public static final String TIMER_AFTER = "AFTER";
    /**
     * 固定间隔定时器：上一次计划时间 + 间隔。
     */
    public static final String TIMER_FIXED = "FIXED";
    /**
     * 指定时间点定时器：第一阶段只支持每日固定时间点集合，例如 {@code ["09:00","18:00"]}。
     */
    public static final String TIMER_POINT_IN_TIME = "POINT_IN_TIME";

    private final String timerType;
    private final String timerValue;
    private final Integer maxCount;
    private final String timezone;

    private EventConfig(String timerType, String timerValue, Integer maxCount, String timezone) {
        this.timerType = timerType;
        this.timerValue = timerValue;
        this.maxCount = maxCount;
        this.timezone = timezone;
    }

    /**
     * 解析事件配置，格式非法即抛出流程异常（保存期与运行期共用）。
     * <p>
     * node_config 为空、缺少 event 段或 event 段为空对象时返回空配置，表示无定时信息（消息事件允许为空）。
     *
     * @param nodeConfig 节点配置 JSON
     * @return 事件配置
     */
    @SuppressWarnings("unchecked")
    public static EventConfig parse(String nodeConfig) {
        if (StringUtils.isEmpty(nodeConfig)) {
            return empty();
        }
        Map<String, Object> root = FlowEngine.jsonConvert.strToMap(nodeConfig);
        if (MapUtil.isEmpty(root)) {
            return empty();
        }
        Object section = root.get("event");
        if (!(section instanceof Map) || MapUtil.isEmpty((Map<String, Object>) section)) {
            return empty();
        }
        Map<String, Object> event = (Map<String, Object>) section;

        String timerType = str(event.get("timerType"));
        String timerValue = str(event.get("timerValue"));
        String timezone = str(event.get("timezone"));
        Integer maxCount = toPositiveInteger(event.get("maxCount"), ExceptionCons.ILLEGAL_EVENT_MAX_COUNT);

        if (StringUtils.isEmpty(timerType)) {
            return new EventConfig(null, null, maxCount, timezone);
        }
        boolean typeOk = TIMER_AFTER.equals(timerType) || TIMER_FIXED.equals(timerType)
            || TIMER_POINT_IN_TIME.equals(timerType);
        AssertUtil.isTrue(!typeOk, ExceptionCons.ILLEGAL_EVENT_TIMER_TYPE);
        AssertUtil.isTrue(StringUtils.isEmpty(timerValue), ExceptionCons.NULL_EVENT_TIMER_VALUE);
        return new EventConfig(timerType, timerValue, maxCount, timezone);
    }

    /**
     * 是否配置了定时触发。
     *
     * @return 配置了 timerType 时返回 {@code true}
     */
    public boolean hasTimer() {
        return StringUtils.isNotEmpty(timerType);
    }

    private static EventConfig empty() {
        return new EventConfig(null, null, null, null);
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer toPositiveInteger(Object value, String message) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            int num = ((Number) value).intValue();
            AssertUtil.isTrue(num <= 0, message);
            return num;
        }
        Integer parsed = null;
        try {
            parsed = Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            // 非数字统一交给下方断言报错
        }
        AssertUtil.isTrue(parsed == null || parsed <= 0, message);
        return parsed;
    }
}

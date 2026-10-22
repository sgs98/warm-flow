package org.dromara.warm.flow.core.timer;

import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.service.event.EventConfig;
import org.dromara.warm.flow.core.utils.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内置定时等待策略注册表。
 * <p>
 * 提供 {@link EventConfig} 三种定时类型（AFTER / FIXED / POINT_IN_TIME）的下次触发时间计算：
 * <ul>
 *     <li>{@code AFTER}：{@code currentTime + 时长}，单次延迟；</li>
 *     <li>{@code FIXED}：{@code 上一次计划时间 + 时长}，避免执行延迟造成时间漂移；</li>
 *     <li>{@code POINT_IN_TIME}：在指定时区的时间点集合中，选择下一个严格大于上次计划触发时间的时间点。
 *     第一阶段仅支持每日时间点集合（如 {@code ["09:00","18:00"]}），不支持完整 Cron。</li>
 * </ul>
 * 不在 core 引入第三方 Cron 依赖，时间解析统一使用 JDK {@code java.time} 与 ISO-8601。
 *
 * @author may
 */
public final class TimerScheduleStrategies {

    /**
     * 策略缓存：定时类型 -> 策略实例。
     */
    private static final Map<String, TimerScheduleStrategy> STRATEGIES = new ConcurrentHashMap<>();

    static {
        STRATEGIES.put(EventConfig.TIMER_AFTER, new AfterStrategy());
        STRATEGIES.put(EventConfig.TIMER_FIXED, new FixedStrategy());
        STRATEGIES.put(EventConfig.TIMER_POINT_IN_TIME, new PointInTimeStrategy());
    }

    private TimerScheduleStrategies() {
    }

    /**
     * 按定时类型计算下一次触发时间。
     *
     * @param triggerType 定时类型（AFTER / FIXED / POINT_IN_TIME）
     * @param context     策略计算入参
     * @return 下一次触发时间
     */
    public static Date nextTriggerAt(String triggerType, TimerContext context) {
        TimerScheduleStrategy strategy = STRATEGIES.get(triggerType);
        if (strategy == null) {
            throw new FlowException(ExceptionCons.ILLEGAL_EVENT_TIMER_TYPE);
        }
        return strategy.nextTriggerAt(context);
    }

    /**
     * 单次延迟策略：当前时间 + ISO-8601 时长。
     */
    static class AfterStrategy implements TimerScheduleStrategy {

        @Override
        public Date nextTriggerAt(TimerContext context) {
            Duration duration = parseDuration(context.getTriggerInterval());
            Date base = context.getCurrentTime() != null ? context.getCurrentTime() : new Date();
            return new Date(base.getTime() + duration.toMillis());
        }
    }

    /**
     * 固定间隔策略：上一次计划时间 + ISO-8601 时长（无漂移）。
     */
    static class FixedStrategy implements TimerScheduleStrategy {

        @Override
        public Date nextTriggerAt(TimerContext context) {
            Duration duration = parseDuration(context.getTriggerInterval());
            Date base = context.getPreviousTriggerAt() != null
                ? context.getPreviousTriggerAt()
                : (context.getCurrentTime() != null ? context.getCurrentTime() : new Date());
            return new Date(base.getTime() + duration.toMillis());
        }
    }

    /**
     * 每日时间点策略：选择下一个严格大于参考触发时间的时间点。
     */
    static class PointInTimeStrategy implements TimerScheduleStrategy {

        @Override
        public Date nextTriggerAt(TimerContext context) {
            List<LocalTime> points = parseTimePoints(context.getTriggerInterval());
            ZoneId zoneId = resolveZone(context.getTimezone());
            Date reference = context.getPreviousTriggerAt() != null
                ? context.getPreviousTriggerAt()
                : (context.getCurrentTime() != null ? context.getCurrentTime() : new Date());

            ZonedDateTime referenceTime = reference.toInstant().atZone(zoneId);
            LocalDate day = referenceTime.toLocalDate();
            // 最多向后找 3 天即可覆盖跨天场景，理论上第 2 天必然命中
            for (int i = 0; i < 3; i++) {
                for (LocalTime point : points) {
                    ZonedDateTime candidate = LocalDateTime.of(day, point).atZone(zoneId);
                    if (candidate.toInstant().isAfter(referenceTime.toInstant())) {
                        return Date.from(candidate.toInstant());
                    }
                }
                day = day.plusDays(1);
            }
            throw new FlowException(ExceptionCons.ILLEGAL_EVENT_TIME_POINT);
        }
    }

    /**
     * 解析 ISO-8601 时长，如 {@code PT30M}、{@code P3D}。
     */
    private static Duration parseDuration(String value) {
        if (StringUtils.isEmpty(value)) {
            throw new FlowException(ExceptionCons.NULL_EVENT_TIMER_VALUE);
        }
        try {
            return Duration.parse(value.trim());
        } catch (Exception e) {
            throw new FlowException(ExceptionCons.ILLEGAL_EVENT_TIMER_DURATION);
        }
    }

    /**
     * 解析每日时间点集合，支持 JSON 数组（{@code ["09:00","18:00"]}）与逗号分隔（{@code 09:00,18:00}）两种写法。
     */
    private static List<LocalTime> parseTimePoints(String value) {
        if (StringUtils.isEmpty(value)) {
            throw new FlowException(ExceptionCons.NULL_EVENT_TIMER_VALUE);
        }
        String normalized = value.trim()
            .replace('[', ' ')
            .replace(']', ' ')
            .replace('"', ' ')
            .replace('，', ',');
        Set<LocalTime> points = new LinkedHashSet<>();
        for (String part : normalized.split(",")) {
            String item = part.trim();
            if (item.isEmpty()) {
                continue;
            }
            LocalTime time = parseTimePoint(item);
            if (time != null) {
                points.add(time);
            }
        }
        if (points.isEmpty()) {
            throw new FlowException(ExceptionCons.ILLEGAL_EVENT_TIME_POINT);
        }
        return new ArrayList<>(points);
    }

    /**
     * 解析单个 {@code HH:mm} 时间点，非法格式返回 {@code null} 并由调用方聚合校验。
     */
    private static LocalTime parseTimePoint(String item) {
        if (!item.matches("\\d{1,2}:\\d{2}")) {
            throw new FlowException(ExceptionCons.ILLEGAL_EVENT_TIME_POINT);
        }
        String[] parts = item.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            throw new FlowException(ExceptionCons.ILLEGAL_EVENT_TIME_POINT);
        }
        return LocalTime.of(hour, minute);
    }

    /**
     * 解析时区，空值回退系统默认时区；非法时区统一转流程异常，保存期试算即可拦截。
     */
    private static ZoneId resolveZone(String timezone) {
        if (StringUtils.isEmpty(timezone)) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (Exception e) {
            throw new FlowException(ExceptionCons.ILLEGAL_EVENT_TIMEZONE);
        }
    }
}

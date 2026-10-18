package org.dromara.warm.flow.core.timer;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * 定时策略计算入参。
 * <p>
 * 统一承载 {@link TimerScheduleStrategy#nextTriggerAt(TimerContext)} 所需的上下文，
 * 输入输出均使用 Java {@link Date}，持久化层再按各数据库方言映射。
 *
 * @author may
 */
@Getter
@Setter
@Accessors(chain = true)
public class TimerContext {

    /**
     * 上一次计划触发时间；首次触发时可为 {@code null}。
     */
    private Date previousTriggerAt;

    /**
     * 当前时间（应用传入的 now）。
     */
    private Date currentTime;

    /**
     * 触发策略原始配置：AFTER / FIXED 为 ISO-8601 时长（如 {@code PT30M}、{@code P3D}），
     * POINT_IN_TIME 为每日时间点集合（如 {@code ["09:00","18:00"]}）。
     */
    private String triggerInterval;

    /**
     * 计算使用的时区（如 {@code Asia/Shanghai}），为空时使用系统默认时区。
     */
    private String timezone;

    /**
     * 已触发次数。
     */
    private Integer repeatCount;
}

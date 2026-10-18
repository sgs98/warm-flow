package org.dromara.warm.flow.core.timer;

import org.dromara.warm.flow.core.exception.FlowException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 定时等待策略单元测试：AFTER / FIXED / POINT_IN_TIME 的下次触发时间计算。
 *
 * @author may
 */
class TimerScheduleStrategiesTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static Date at(int year, int month, int day, int hour, int minute) {
        return Date.from(ZonedDateTime.of(LocalDateTime.of(year, month, day, hour, minute),
            ZONE).toInstant());
    }

    @Test
    void afterAddsDurationToCurrentTime() {
        Date now = at(2026, 10, 16, 9, 0);
        Date next = TimerScheduleStrategies.nextTriggerAt("AFTER", new TimerContext()
            .setCurrentTime(now)
            .setTriggerInterval("P3D"));
        assertEquals(at(2026, 10, 19, 9, 0), next);
    }

    @Test
    void afterSupportsMinutesAndHours() {
        Date now = at(2026, 10, 16, 9, 0);
        assertEquals(at(2026, 10, 16, 9, 30), TimerScheduleStrategies.nextTriggerAt("AFTER",
            new TimerContext().setCurrentTime(now).setTriggerInterval("PT30M")));
        assertEquals(at(2026, 10, 16, 10, 0), TimerScheduleStrategies.nextTriggerAt("AFTER",
            new TimerContext().setCurrentTime(now).setTriggerInterval("PT1H")));
    }

    @Test
    void fixedUsesPreviousPlannedTimeToAvoidDrift() {
        Date previousPlanned = at(2026, 10, 16, 9, 0);
        // 执行已延迟到 09:07，下一次仍按计划时间 + 间隔，不按执行时间累加
        Date next = TimerScheduleStrategies.nextTriggerAt("FIXED", new TimerContext()
            .setPreviousTriggerAt(previousPlanned)
            .setCurrentTime(at(2026, 10, 16, 9, 7))
            .setTriggerInterval("PT1H"));
        assertEquals(at(2026, 10, 16, 10, 0), next);
    }

    @Test
    void fixedFallsBackToCurrentTimeWhenFirstTrigger() {
        Date now = at(2026, 10, 16, 9, 0);
        Date next = TimerScheduleStrategies.nextTriggerAt("FIXED", new TimerContext()
            .setCurrentTime(now)
            .setTriggerInterval("P1D"));
        assertEquals(at(2026, 10, 17, 9, 0), next);
    }

    @Test
    void pointInTimePicksNextPointSameDay() {
        Date reference = at(2026, 10, 16, 10, 0);
        Date next = TimerScheduleStrategies.nextTriggerAt("POINT_IN_TIME", new TimerContext()
            .setPreviousTriggerAt(reference)
            .setCurrentTime(reference)
            .setTriggerInterval("[\"09:00\",\"18:00\"]")
            .setTimezone("Asia/Shanghai"));
        assertEquals(at(2026, 10, 16, 18, 0), next);
    }

    @Test
    void pointInTimeRollsToNextDay() {
        Date reference = at(2026, 10, 16, 19, 0);
        Date next = TimerScheduleStrategies.nextTriggerAt("POINT_IN_TIME", new TimerContext()
            .setPreviousTriggerAt(reference)
            .setCurrentTime(reference)
            .setTriggerInterval("[\"09:00\",\"18:00\"]")
            .setTimezone("Asia/Shanghai"));
        assertEquals(at(2026, 10, 17, 9, 0), next);
    }

    @Test
    void pointInTimeUsesCurrentTimeWhenFirstTrigger() {
        Date now = at(2026, 10, 16, 8, 0);
        Date next = TimerScheduleStrategies.nextTriggerAt("POINT_IN_TIME", new TimerContext()
            .setCurrentTime(now)
            .setTriggerInterval("09:00,18:00")
            .setTimezone("Asia/Shanghai"));
        assertEquals(at(2026, 10, 16, 9, 0), next);
    }

    @Test
    void pointInTimeRespectsTimezone() {
        // 参考时间为上海 18:05（= UTC 10:05），UTC 当天 09:00 已过，取次日 UTC 09:00
        Date reference = at(2026, 10, 16, 18, 5);
        Date next = TimerScheduleStrategies.nextTriggerAt("POINT_IN_TIME", new TimerContext()
            .setPreviousTriggerAt(reference)
            .setCurrentTime(reference)
            .setTriggerInterval("[\"09:00\"]")
            .setTimezone("UTC"));
        ZonedDateTime expected = LocalDateTime.of(LocalDate.of(2026, 10, 17), LocalTime.of(9, 0))
            .atZone(ZoneId.of("UTC"));
        assertEquals(Date.from(expected.toInstant()), next);
    }

    @Test
    void illegalDurationThrows() {
        Date now = new Date();
        assertThrows(FlowException.class, () -> TimerScheduleStrategies.nextTriggerAt("AFTER",
            new TimerContext().setCurrentTime(now).setTriggerInterval("3days")));
        assertThrows(FlowException.class, () -> TimerScheduleStrategies.nextTriggerAt("AFTER",
            new TimerContext().setCurrentTime(now)));
    }

    @Test
    void illegalTimePointThrows() {
        Date now = new Date();
        assertThrows(FlowException.class, () -> TimerScheduleStrategies.nextTriggerAt(
            "POINT_IN_TIME", new TimerContext().setCurrentTime(now).setTriggerInterval("[\"25:00\"]")
                .setTimezone("Asia/Shanghai")));
        assertThrows(FlowException.class, () -> TimerScheduleStrategies.nextTriggerAt(
            "POINT_IN_TIME", new TimerContext().setCurrentTime(now).setTriggerInterval("[\"abc\"]")
                .setTimezone("Asia/Shanghai")));
    }

    @Test
    void unknownTriggerTypeThrows() {
        assertThrows(FlowException.class, () -> TimerScheduleStrategies.nextTriggerAt("CRON",
            new TimerContext().setCurrentTime(new Date()).setTriggerInterval("PT1H")));
    }

    @Test
    void computedTimeIsStrictlyAfterReference() {
        Date reference = at(2026, 10, 16, 18, 0);
        Date next = TimerScheduleStrategies.nextTriggerAt("POINT_IN_TIME", new TimerContext()
            .setPreviousTriggerAt(reference)
            .setCurrentTime(reference)
            .setTriggerInterval("[\"18:00\"]")
            .setTimezone("Asia/Shanghai"));
        assertTrue(next.after(reference));
    }
}

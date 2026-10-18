package org.dromara.warm.flow.core.service.event;

import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventConfigTest {

    private FlowTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new FlowTestHarness();
    }

    @AfterEach
    void tearDown() {
        harness.close();
    }

    @Test
    void parseAfterTimer() {
        EventConfig c = EventConfig.parse(
            "{\"event\":{\"timerType\":\"AFTER\",\"timerValue\":\"P3D\",\"timezone\":\"Asia/Shanghai\"}}");
        assertTrue(c.hasTimer());
        assertEquals("AFTER", c.getTimerType());
        assertEquals("P3D", c.getTimerValue());
        assertEquals("Asia/Shanghai", c.getTimezone());
    }

    @Test
    void parseFixedTimerWithMaxCount() {
        EventConfig c = EventConfig.parse(
            "{\"event\":{\"timerType\":\"FIXED\",\"timerValue\":\"PT1H\",\"maxCount\":24}}");
        assertTrue(c.hasTimer());
        assertEquals("FIXED", c.getTimerType());
        assertEquals(24, c.getMaxCount());
    }

    @Test
    void parsePointInTimeTimer() {
        EventConfig c = EventConfig.parse(
            "{\"event\":{\"timerType\":\"POINT_IN_TIME\",\"timerValue\":\"[\\\"09:00\\\",\\\"18:00\\\"]\"}}");
        assertTrue(c.hasTimer());
        assertEquals("POINT_IN_TIME", c.getTimerType());
    }

    @Test
    void emptyForMessageEvent() {
        assertFalse(EventConfig.parse(null).hasTimer());
        assertFalse(EventConfig.parse("").hasTimer());
        assertFalse(EventConfig.parse("{}").hasTimer());
        assertFalse(EventConfig.parse("{\"event\":{}}").hasTimer());
    }

    @Test
    void rejectIllegalTimerType() {
        assertThrows(FlowException.class, () -> EventConfig.parse(
            "{\"event\":{\"timerType\":\"CRON\",\"timerValue\":\"x\"}}"));
    }

    @Test
    void rejectMissingTimerValue() {
        assertThrows(FlowException.class, () -> EventConfig.parse(
            "{\"event\":{\"timerType\":\"AFTER\"}}"));
    }

    @Test
    void rejectIllegalMaxCount() {
        assertThrows(FlowException.class, () -> EventConfig.parse(
            "{\"event\":{\"timerType\":\"FIXED\",\"timerValue\":\"PT1H\",\"maxCount\":0}}"));
    }
}

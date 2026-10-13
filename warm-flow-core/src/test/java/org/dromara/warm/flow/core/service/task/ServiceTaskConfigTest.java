package org.dromara.warm.flow.core.service.task;

import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceTaskConfigTest {

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
    void parseClassConfig() {
        ServiceTaskConfig c = ServiceTaskConfig.parse(
            "{\"serviceTask\":{\"type\":\"class\",\"handler\":\"a.B\"}}");
        assertEquals("class", c.getType());
        assertEquals("a.B", c.getHandler());
    }

    @Test
    void parseExpressionConfigWithResultVariable() {
        ServiceTaskConfig c = ServiceTaskConfig.parse(
            "{\"serviceTask\":{\"type\":\"expression\",\"expression\":\"#{@x.y()}\",\"resultVariable\":\"no\"}}");
        assertEquals("expression", c.getType());
        assertEquals("#{@x.y()}", c.getExpression());
        assertEquals("no", c.getResultVariable());
    }

    @Test
    void rejectMissingSection() {
        assertThrows(FlowException.class, () -> ServiceTaskConfig.parse("{}"));
        assertThrows(FlowException.class, () -> ServiceTaskConfig.parse(null));
    }

    @Test
    void rejectIllegalType() {
        assertThrows(FlowException.class, () -> ServiceTaskConfig.parse(
            "{\"serviceTask\":{\"type\":\"script\",\"expression\":\"x\"}}"));
    }

    @Test
    void rejectClassWithoutHandler() {
        assertThrows(FlowException.class, () -> ServiceTaskConfig.parse(
            "{\"serviceTask\":{\"type\":\"class\"}}"));
    }

    @Test
    void rejectResultVariableOnNonExpression() {
        assertThrows(FlowException.class, () -> ServiceTaskConfig.parse(
            "{\"serviceTask\":{\"type\":\"class\",\"handler\":\"a.B\",\"resultVariable\":\"no\"}}"));
    }
}

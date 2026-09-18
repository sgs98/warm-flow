package org.dromara.warm.flow.core.utils;

import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.exception.FlowException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * core 内置条件表达式特征测试：eq/gt/ge/lt/le/ne/like/notLike 的数值与字符串语义、
 * 变量缺失报错、空表达式与未知前缀返回 false、办理人表达式直传。
 * 网关路由与票签表达式均建立在这些语义之上。
 *
 * @author warm
 */
class ExpressionUtilCharacteristicTest {

    private static Map<String, Object> vars() {
        Map<String, Object> variable = new HashMap<>();
        variable.put("days", 3);
        variable.put("user", "zhangsan");
        return variable;
    }

    @Test
    void eq_numericAndString() {
        assertTrue(ExpressionUtil.evalCondition("eq@@days|3", vars()));
        assertFalse(ExpressionUtil.evalCondition("eq@@days|4", vars()));
        assertTrue(ExpressionUtil.evalCondition("eq@@user|zhangsan", vars()));
        assertFalse(ExpressionUtil.evalCondition("eq@@user|lisi", vars()));
    }

    @Test
    void comparison_gtGeLtLe() {
        assertTrue(ExpressionUtil.evalCondition("gt@@days|2", vars()));
        assertFalse(ExpressionUtil.evalCondition("gt@@days|3", vars()));
        assertTrue(ExpressionUtil.evalCondition("ge@@days|3", vars()));
        assertTrue(ExpressionUtil.evalCondition("lt@@days|4", vars()));
        assertFalse(ExpressionUtil.evalCondition("lt@@days|3", vars()));
        assertTrue(ExpressionUtil.evalCondition("le@@days|3", vars()));
    }

    @Test
    void ne_like_notLike() {
        assertTrue(ExpressionUtil.evalCondition("ne@@user|lisi", vars()));
        assertFalse(ExpressionUtil.evalCondition("ne@@user|zhangsan", vars()));
        assertTrue(ExpressionUtil.evalCondition("like@@user|zhang", vars()));
        assertFalse(ExpressionUtil.evalCondition("like@@user|lisi", vars()));
        assertTrue(ExpressionUtil.evalCondition("notLike@@user|lisi", vars()));
        assertFalse(ExpressionUtil.evalCondition("notLike@@user|zhang", vars()));
    }

    @Test
    void missingVariable_throwsNullConditionValue() {
        FlowException emptyMap = assertThrows(FlowException.class,
                () -> ExpressionUtil.evalCondition("eq@@days|3", new HashMap<>()));
        assertEquals(ExceptionCons.NULL_CONDITION_VALUE, emptyMap.getMessage());

        FlowException absentKey = assertThrows(FlowException.class,
                () -> ExpressionUtil.evalCondition("eq@@amount|3", vars()));
        assertEquals(ExceptionCons.NULL_CONDITION_VALUE, absentKey.getMessage());
    }

    @Test
    void blankOrUnknownExpression_evaluatesFalse() {
        assertFalse(ExpressionUtil.evalCondition("", vars()));
        assertFalse(ExpressionUtil.evalCondition(null, vars()));
        // 未知前缀无策略匹配，返回 null → false（不抛错）
        assertFalse(ExpressionUtil.evalCondition("unknown@@days|3", vars()));
    }

    @Test
    void evalVariable_plainHandler_passthrough() {
        List<String> resolved = ExpressionUtil.evalVariable("zhangsan", Map.of());
        assertEquals(List.of("zhangsan"), resolved, "普通办理人字符串直传");
    }
}

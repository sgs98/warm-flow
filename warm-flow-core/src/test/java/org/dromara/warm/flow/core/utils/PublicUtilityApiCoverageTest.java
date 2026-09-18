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

import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.ChartStatus;
import org.dromara.warm.flow.core.enums.ConditionType;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.dto.ApiResult;
import org.dromara.warm.flow.core.dto.FlowPage;
import org.dromara.warm.flow.core.listener.ValueHolder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * core 工具类与枚举公共辅助 API 的直接覆盖测试。
 *
 * @author warm
 */
class PublicUtilityApiCoverageTest {

    @Test
    void arrayCollectionAndMapHelpers_coverEmptyDefaultsAndComposition() {
        String[] defaults = {"default"};
        assertTrue(ArrayUtil.isEmpty(null));
        assertTrue(ArrayUtil.isNotEmpty(new String[]{"x"}));
        assertArrayEquals(defaults, ArrayUtil.emptyDefault(new String[0], defaults));
        assertTrue(ArrayUtil.isArray(new int[]{1}));
        assertFalse(ArrayUtil.isArray("x"));
        assertArrayEquals(new String[]{"a", "b"}, ArrayUtil.strToArrAy("a,b", ","));

        assertEquals("a", CollUtil.getOne(List.of("a", "b")));
        assertNull(CollUtil.getOne(List.of()));
        assertTrue(CollUtil.containsAny(List.of("a", "b"), "x", "b"));
        assertTrue(CollUtil.containsAny(List.of("a"), List.of("a")));
        assertTrue(CollUtil.notContainsAny(List.of("a"), List.of("b")));
        assertEquals(List.of("x", "a", "b"), CollUtil.listAddToNew(List.of("a", "b"), "x"));
        assertEquals(List.of("a", "b", "c"), CollUtil.listAddListsToNew(List.of("c"), List.of(List.of("a", "b"))));
        assertEquals("a|b", CollUtil.strListToStr(List.of("a", "b"), "|"));
        assertEquals(List.of(List.of(1, 2), List.of(3)), CollUtil.split(List.of(1, 2, 3), 2));

        assertEquals(Map.of("a", 3), StreamUtils.merge(Map.of("a", 1), Map.of("a", 2),
            (left, right) -> left + right));
        assertEquals(Map.of("x", 1), MapUtil.newAndPut("x", 1));
        assertEquals(Map.of("x", 1), MapUtil.clone(Map.of("x", 1)));
        assertEquals(Map.of("fallback", 1), MapUtil.emptyDefault(Map.of(), Map.of("fallback", 1)));
        assertEquals(Map.of("a", 1, "b", 2), MapUtil.<String, Integer>mergeAll("a", 1, "b", 2));
    }

    @Test
    void stringMathAndObjectHelpers_coverBoundaryContracts() {
        assertEquals("fallback", StringUtils.nvl(null, "fallback"));
        assertTrue(StringUtils.hasEmpty("ok", " "));
        assertTrue(StringUtils.isAllNotEmpty("a", "b"));
        assertEquals("text", StringUtils.trim(" text "));
        assertTrue(StringUtils.containsAny(List.of("a", "b"), "b"));
        assertEquals("cde", StringUtils.substring("abcde", -3));
        assertEquals("bc", StringUtils.substring("abcde", 1, -2));
        assertEquals(Set.of("a", "b"), StringUtils.str2Set("a,b,a", ","));
        assertEquals(List.of("a", "b"), StringUtils.str2List(" a, b ", ","));
        assertEquals("hello_world", StringUtils.toUnderScoreCase("helloWorld"));
        assertEquals("HelloWorld", StringUtils.convertToCamelCase("HELLO_WORLD"));
        assertEquals("helloWorld", StringUtils.toCamelCase("HELLO_WORLD"));
        assertEquals("007", StringUtils.padLeft(7, 3));
        assertEquals("a-b-c", StringUtils.join(new Object[]{"a", "b", "c"}, "-"));
        assertEquals("b-c", StringUtils.join(new Object[]{"a", "b", "c"}, "-", 1, 3));

        assertTrue(MathUtil.isNumeric(" 1.5 "));
        assertFalse(MathUtil.isNumeric("x"));
        assertEquals(0, MathUtil.determineSize("1.0", "1"));
        assertTrue(MathUtil.isZero("0.0"));
        assertTrue(MathUtil.isHundred("100"));
        assertTrue(MathUtil.isBetweenZeroAndHundred("50"));
        assertFalse(MathUtil.isBetweenZeroAndHundred("101"));
        assertTrue(ObjectUtil.isStrTrue("true"));
        assertFalse(ObjectUtil.isStrTrue("false"));
        assertEquals("fallback", ObjectUtil.defaultNull(null, () -> "fallback"));
    }

    @Test
    void streamHelpers_coverMappingGroupingAndMerging() {
        List<Integer> values = List.of(1, 2, 3, 4);
        assertEquals(List.of(2, 4), StreamUtils.filter(values, value -> value % 2 == 0));
        assertEquals(3, StreamUtils.filterOne(values, value -> value > 2));
        assertEquals("1:2:3:4", StreamUtils.join(values, String::valueOf, ":"));
        assertEquals(List.of(4, 3, 2, 1), StreamUtils.sorted(values, (left, right) -> right - left));
        assertEquals(Map.of(1, 1, 2, 2, 3, 3, 4, 4), StreamUtils.toIdentityMap(values, value -> value));
        assertEquals(Map.of("odd", List.of(1, 3), "even", List.of(2, 4)),
            StreamUtils.groupByKey(values, value -> value % 2 == 0 ? "even" : "odd"));
        assertEquals(List.of(2, 4), StreamUtils.groupByKeyFilter(value -> value % 2 == 0, values,
            value -> "even").get("even"));
        assertEquals(1, StreamUtils.groupBy2Key(values, value -> value % 2, value -> value > 2)
            .get(0).get(true).size());
        assertEquals(3, StreamUtils.group2Map(values, value -> value % 2, value -> value > 2)
            .get(1).get(true));
        assertEquals(List.of("1", "2", "3", "4"), StreamUtils.toList(values, String::valueOf));
        assertEquals(List.of(1, 10, 2, 20), StreamUtils.toListAll(List.of(1, 2),
            value -> List.of(value, value * 10)));
        assertEquals(Set.of(1, 0), StreamUtils.toSet(values, value -> value % 2));
        assertArrayEquals(new Integer[]{1, 2, 3, 4}, StreamUtils.toArray(values, Integer[]::new));
        assertEquals(Map.of(1, 3, 2, 2), StreamUtils.merge(Map.of(1, 1), Map.of(1, 2, 2, 2),
            (left, right) -> (left == null ? 0 : left) + (right == null ? 0 : right)));
    }

    @Test
    void enumHelpers_keepKeyValueAndClassificationContracts() {
        assertTrue(ActivityStatus.isActivity(ActivityStatus.ACTIVITY.getKey()));
        assertTrue(ActivityStatus.isSuspended(ActivityStatus.SUSPENDED.getKey()));
        assertEquals("eq", ConditionType.getKeyByValue("等于"));
        assertEquals(ConditionType.EQ, ConditionType.getByKey("eq"));
        assertEquals("审批中", FlowStatus.getValueByKey("1"));
        assertTrue(FlowStatus.isFinished(FlowStatus.FINISHED.getKey()));
        assertEquals(NodeType.PARALLEL, NodeType.getByKey(NodeType.PARALLEL.getKey()));
        assertTrue(NodeType.isGateWay(NodeType.INCLUSIVE.getKey()));
        assertTrue(NodeType.isGateWayParallel(NodeType.PARALLEL.getKey()));
        assertEquals(SkipType.REJECT, SkipType.getByKey("REJECT"));
        assertTrue(SkipType.isPass("PASS"));
        assertTrue(SkipType.isReject("REJECT"));
        assertTrue(SkipType.isNone("NONE"));
        assertEquals(CooperateType.COUNTERSIGN, CooperateType.getByKey(4));
        assertTrue(CooperateType.isOrSign("0"));
        assertTrue(CooperateType.isCountersign("100"));
        assertTrue(CooperateType.isVoteSignPassRatio("50"));
        assertTrue(CooperateType.isVoteSignPassCount("passCount=2"));
        assertTrue(CooperateType.isVoteSignRejectCount("rejectCount=1"));
        assertTrue(CooperateType.isVoteSignDefault("default@@x"));
        assertTrue(CooperateType.isVoteSignRejectSpel("spel@@x"));
        assertTrue(CooperateType.isSequenceSign("50@@sequence"));
        assertEquals("50", CooperateType.removeSequence("50@@sequence"));
        assertTrue(ChartStatus.isDone(ChartStatus.DONE.getKey()));
        assertFalse(ChartStatus.isNotDone(ChartStatus.DONE.getKey()));
    }

    @Test
    void reflectionAssertionExceptionAndSpiHelpers_coverPublicUtilitySurface() throws Exception {
        assertEquals(String.class, ClassUtil.getClazz("java.lang.String"));
        assertEquals(null, ClassUtil.getClazz("missing.Type"));
        FlowPage<String> original = new FlowPage<String>(new ArrayList<>(List.of("x")), 1);
        FlowPage<String> copy = ClassUtil.clone(original);
        assertEquals(original.getRows(), copy.getRows());
        assertTrue(ClassUtil.findClasses("org.dromara.warm.flow.core.utils").contains(StringUtils.class));

        AssertUtil.isNull("value", "unused");
        AssertUtil.isNotNull(null, "unused");
        AssertUtil.isFalse(true, "unused");
        AssertUtil.isTrue(false, "unused");
        AssertUtil.isNotEmpty(List.of(), "unused");
        AssertUtil.isEmpty(List.of("value"), "unused");
        AssertUtil.contains(List.of("a"), "b", "unused");
        AssertUtil.notContains(List.of("a"), "a", "unused");
        assertTrue(ExceptionUtil.getExceptionMessage(new IllegalArgumentException("bad")).contains("bad"));
        assertEquals("prefix: bad", ExceptionUtil.handleMsg("prefix", new IllegalArgumentException("bad")));
        assertEquals("bad", ExceptionUtil.handleMsg("", new IllegalArgumentException("bad")));

        assertTrue(ServiceLoaderUtil.load(Runnable.class) != null);
        assertNull(ServiceLoaderUtil.loadFirst(Runnable.class));
        assertTrue(ServiceLoaderUtil.loadList(Runnable.class).isEmpty());
        assertEquals(ServiceLoaderUtil.getContextClassLoader(), ServiceLoaderUtil.getClassLoader());
        assertEquals(200, ApiResult.ok().getCode());
        assertTrue(ApiResult.isSuccess(ApiResult.ok("data")));
        assertTrue(ApiResult.isError(ApiResult.fail("bad")));
        assertEquals("bad", ApiResult.fail("bad").getMsg());

        ValueHolder holder = new ValueHolder();
        ListenerUtil.getListenerPath("example.Listener({\"key\":1})", holder);
        assertEquals("example.Listener", holder.getPath());
        assertEquals("{\"key\":1}", holder.getParams());
    }
}

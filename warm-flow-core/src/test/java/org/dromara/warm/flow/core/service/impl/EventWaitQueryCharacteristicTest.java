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
package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.EventCons;
import org.dromara.warm.flow.core.dto.EventWaitQuery;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.enums.EventType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.utils.page.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 事件等待记录（定时任务）只读查询特征测试：默认只查定时类、多条件组合、
 * businessId / flowCode 解析下推、排序白名单与非法入参拒绝。
 *
 * @author may
 */
class EventWaitQueryCharacteristicTest {

    /**
     * 查询基准时间，避免依赖系统时钟。
     */
    private static final Date BASE = new Date(1_700_000_000_000L);

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
    void defaultQueryOnlyReturnsTimerFamilyOrderedByNextTriggerAt() {
        EventWait message = saveWait(1001L, null, EventType.MESSAGE_CATCH, null, BASE);
        EventWait late = saveWait(1001L, null, EventType.TIMER_START, addHours(BASE, 2), BASE);
        EventWait early = saveWait(1001L, null, EventType.TIMER_CATCH, addHours(BASE, 1), BASE);
        EventWait boundary = saveWait(1001L, null, EventType.TIMER_BOUNDARY, addHours(BASE, 3), BASE);

        Page<EventWait> page = FlowEngine.workflow().pageEventWaits(null);

        assertEquals(3, page.getTotal());
        assertEquals(List.of(early.getId(), late.getId(), boundary.getId())
            , page.getList().stream().map(EventWait::getId).collect(Collectors.toList()));
        assertTrue(page.getList().stream().noneMatch(wait -> wait.getId().equals(message.getId())));

        EventWaitQuery secondPage = new EventWaitQuery();
        secondPage.setPageNum(2);
        secondPage.setPageSize(2);
        Page<EventWait> page2 = FlowEngine.workflow().pageEventWaits(secondPage);
        assertEquals(3, page2.getTotal());
        assertEquals(2, page2.getPageNum());
        assertEquals(2, page2.getPageSize());
        assertEquals(List.of(boundary.getId())
            , page2.getList().stream().map(EventWait::getId).collect(Collectors.toList()));

        // 超出范围的页码返回空列表，总数仍为匹配总数
        EventWaitQuery thirdPage = new EventWaitQuery();
        thirdPage.setPageNum(3);
        thirdPage.setPageSize(2);
        Page<EventWait> page3 = FlowEngine.workflow().pageEventWaits(thirdPage);
        assertEquals(3, page3.getTotal());
        assertTrue(page3.getList().isEmpty());
    }

    @Test
    void conditionsCombineWithAndAndMultiValueWithOr() {
        EventWait waiting = saveWait(1002L, 2001L, EventType.TIMER_CATCH, addHours(BASE, 1), BASE);
        saveWait(1002L, 2001L, EventType.TIMER_CATCH, addHours(BASE, 1), BASE, EventCons.STATUS_DEAD);
        saveWait(1002L, 2002L, EventType.TIMER_CATCH, addHours(BASE, 1), BASE);

        EventWaitQuery query = new EventWaitQuery();
        query.setInstanceId(2001L);
        query.setNodeCode("cooldown");
        query.setStatuses(List.of(EventCons.STATUS_WAITING));
        query.setNextTriggerAtFrom(addHours(BASE, 1));
        query.setNextTriggerAtTo(addHours(BASE, 30));

        assertEquals(1L, FlowEngine.workflow().countEventWaits(query));
        List<EventWait> list = FlowEngine.workflow().listEventWaits(query);
        assertEquals(1, list.size());
        assertEquals(waiting.getId(), list.get(0).getId());

        // 多值条件取「或」，不同条件之间取「且」
        EventWaitQuery byStatuses = new EventWaitQuery();
        byStatuses.setStatuses(List.of(EventCons.STATUS_WAITING, EventCons.STATUS_DEAD));
        assertEquals(3L, FlowEngine.workflow().countEventWaits(byStatuses));
        byStatuses.setInstanceIds(List.of(2001L));
        assertEquals(2L, FlowEngine.workflow().countEventWaits(byStatuses));
    }

    @Test
    void explicitEventTypesOverrideDefaultTimerFamily() {
        saveWait(1003L, null, EventType.MESSAGE_CATCH, null, BASE);
        saveWait(1003L, null, EventType.TIMER_CATCH, addHours(BASE, 1), BASE);

        EventWaitQuery messageOnly = new EventWaitQuery();
        messageOnly.setEventTypes(List.of(EventType.MESSAGE_CATCH.getValue()));
        assertEquals(1L, FlowEngine.workflow().countEventWaits(messageOnly));
        assertEquals(EventType.MESSAGE_CATCH.getValue()
            , FlowEngine.workflow().listEventWaits(messageOnly).get(0).getEventType());

        // 关闭定时类默认范围后不限事件类型
        EventWaitQuery all = new EventWaitQuery();
        all.setTimerOnly(false);
        assertEquals(2L, FlowEngine.workflow().countEventWaits(all));
    }

    @Test
    void businessIdAndFlowCodeResolveBeforeQuery() {
        Definition definition = FlowEngine.newDef().setFlowCode("queryFlow");
        FlowEngine.defService().save(definition);
        Instance instance = FlowEngine.newIns().setBusinessId("BIZ-100");
        FlowEngine.insService().save(instance);
        EventWait wait = saveWait(definition.getId(), instance.getId(), EventType.TIMER_CATCH, addHours(BASE, 1), BASE);
        saveWait(definition.getId(), 2002L, EventType.TIMER_CATCH, addHours(BASE, 1), BASE);

        EventWaitQuery byBusinessId = new EventWaitQuery();
        byBusinessId.setBusinessId("BIZ-100");
        assertEquals(1L, FlowEngine.workflow().countEventWaits(byBusinessId));
        assertEquals(wait.getId(), FlowEngine.workflow().listEventWaits(byBusinessId).get(0).getId());
        // 解析基于副本，调用方入参可重复复用且不被改写
        assertEquals("BIZ-100", byBusinessId.getBusinessId());
        assertNull(byBusinessId.getInstanceIds());
        assertEquals(1L, FlowEngine.workflow().countEventWaits(byBusinessId));

        EventWaitQuery byFlowCode = new EventWaitQuery();
        byFlowCode.setFlowCode("queryFlow");
        assertEquals(2L, FlowEngine.workflow().countEventWaits(byFlowCode));

        // 解析不到业务 / 定义时按空结果返回，而不是放弃该条件
        EventWaitQuery unknownBusinessId = new EventWaitQuery();
        unknownBusinessId.setBusinessId("BIZ-NOT-EXIST");
        assertEquals(0L, FlowEngine.workflow().countEventWaits(unknownBusinessId));
        assertTrue(FlowEngine.workflow().listEventWaits(unknownBusinessId).isEmpty());
        Page<EventWait> emptyPage = FlowEngine.workflow().pageEventWaits(unknownBusinessId);
        assertEquals(0, emptyPage.getTotal());
        assertTrue(emptyPage.getList().isEmpty());
    }

    @Test
    void orderWhitelistAndSingleRowLookupWork() {
        EventWait late = saveWait(1004L, null, EventType.TIMER_CATCH, addHours(BASE, 2), BASE);
        EventWait early = saveWait(1004L, null, EventType.TIMER_CATCH, addHours(BASE, 1), BASE);

        EventWaitQuery descending = new EventWaitQuery();
        descending.setOrderBy("next_trigger_at");
        descending.setIsAsc("DESC");
        assertEquals(List.of(late.getId(), early.getId())
            , FlowEngine.workflow().listEventWaits(descending).stream()
                .map(EventWait::getId).collect(Collectors.toList()));

        EventWaitQuery byId = new EventWaitQuery();
        byId.setOrderBy("id");
        List<Long> idAsc = FlowEngine.workflow().listEventWaits(byId).stream()
            .map(EventWait::getId).collect(Collectors.toList());
        assertEquals(2, idAsc.size());
        assertTrue(idAsc.get(0) < idAsc.get(1));

        assertEquals(early.getId(), FlowEngine.workflow().getEventWait(early.getId()).getId());
        assertNull(FlowEngine.workflow().getEventWait(null));
    }

    @Test
    void illegalSortAndMutuallyExclusiveConditionsAreRejected() {
        saveWait(1005L, 2003L, EventType.TIMER_CATCH, addHours(BASE, 1), BASE);

        EventWaitQuery illegalOrder = new EventWaitQuery();
        illegalOrder.setOrderBy("id; drop table flow_task");
        assertThrows(FlowException.class, () -> FlowEngine.workflow().countEventWaits(illegalOrder));

        EventWaitQuery illegalDirection = new EventWaitQuery();
        illegalDirection.setIsAsc("DESC; --");
        assertThrows(FlowException.class, () -> FlowEngine.workflow().countEventWaits(illegalDirection));

        EventWaitQuery businessWithInstance = new EventWaitQuery();
        businessWithInstance.setBusinessId("BIZ-100");
        businessWithInstance.setInstanceId(2003L);
        assertThrows(FlowException.class, () -> FlowEngine.workflow().pageEventWaits(businessWithInstance));

        EventWaitQuery flowCodeWithDefinition = new EventWaitQuery();
        flowCodeWithDefinition.setFlowCode("queryFlow");
        flowCodeWithDefinition.setDefinitionIds(List.of(1005L));
        assertThrows(FlowException.class, () -> FlowEngine.workflow().listEventWaits(flowCodeWithDefinition));

        EventWaitQuery illegalPage = new EventWaitQuery();
        illegalPage.setPageSize(0);
        assertThrows(FlowException.class, () -> FlowEngine.workflow().pageEventWaits(illegalPage));
    }

    /**
     * 落一条等待记录，返回回填主键后的实体。
     */
    private EventWait saveWait(Long definitionId, Long instanceId, EventType eventType,
        Date nextTriggerAt, Date createTime) {
        return saveWait(definitionId, instanceId, eventType, nextTriggerAt, createTime, EventCons.STATUS_WAITING);
    }

    /**
     * 落一条指定状态的等待记录，返回回填主键后的实体。
     */
    private EventWait saveWait(Long definitionId, Long instanceId, EventType eventType,
        Date nextTriggerAt, Date createTime, String status) {
        EventWait wait = FlowEngine.newEventWait()
            .setDefinitionId(definitionId)
            .setInstanceId(instanceId)
            .setNodeCode("cooldown")
            .setEventType(eventType.getValue())
            .setStatus(status)
            .setNextTriggerAt(nextTriggerAt)
            .setCreateTime(createTime);
        FlowEngine.eventWaitService().save(wait);
        return wait;
    }

    /**
     * 以小时为单位偏移基准时间。
     */
    private Date addHours(Date base, int hours) {
        return new Date(base.getTime() + hours * 3600_000L);
    }

}

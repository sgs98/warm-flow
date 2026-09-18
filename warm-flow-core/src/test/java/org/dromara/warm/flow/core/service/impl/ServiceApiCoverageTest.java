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
import org.dromara.warm.flow.core.dto.FlowCombine;
import org.dromara.warm.flow.core.entity.Form;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.utils.page.Page;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 核心 Service 公共 API 的直接覆盖测试。
 *
 * @author warm
 */
class ServiceApiCoverageTest {

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
    void nodeAndSkipQueries_coverLookupTraversalAndDeleteApis() {
        var definition = TestFlows.parallelFlow("api-node");
        FlowCombine combine = FlowEngine.defService().getFlowCombine(definition);
        Node a1 = FlowEngine.nodeService().getByDefIdAndNodeCode(definition.getId(), "a1");

        assertEquals(6, FlowEngine.nodeService().getByDefId(definition.getId()).size());
        assertEquals(2, FlowEngine.nodeService().getByNodeCodes(List.of("a1", "b1"), definition.getId()).size());
        assertEquals(6, FlowEngine.nodeService().getPublishByFlowCode("api-node").size());
        assertEquals("start", FlowEngine.nodeService().getStartNode(definition.getId()).getNodeCode());
        assertEquals("end", FlowEngine.nodeService().getEndNode(definition.getId()).getNodeCode());
        assertEquals(2, FlowEngine.nodeService().getBetweenNode(definition.getId()).size());
        assertFalse(FlowEngine.nodeService().previousNodeList(a1.getId()).isEmpty());
        assertFalse(FlowEngine.nodeService().previousNodeList(definition.getId(), "a1").isEmpty());
        assertFalse(FlowEngine.nodeService().suffixNodeList(a1.getId()).isEmpty());
        assertFalse(FlowEngine.nodeService().suffixNodeList(definition.getId(), "a1").isEmpty());
        assertFalse(FlowEngine.nodeService().suffixNodeList("a1", combine).isEmpty());
        assertEquals(2, FlowEngine.nodeService().getFirstBetweenNode(definition.getId(), Map.of()).size());
        assertEquals(List.of("a1"), FlowEngine.nodeService().getNextNodeList(
            definition.getId(), "forkP", null, SkipType.PASS.getKey(), Map.of()).stream()
            .map(Node::getNodeCode).toList());
        assertEquals("a1", FlowEngine.nodeService().getNextNode(definition.getId(), "forkP", "a1",
            SkipType.PASS.getKey()).getNodeCode());
        assertNotNull(FlowEngine.nodeService().getNextByCheckGateway(Map.of(),
            combine.getAllNodes().stream().filter(n -> "forkP".equals(n.getNodeCode())).findFirst().orElseThrow(),
            null, combine));
        assertEquals("joinP", FlowEngine.nodeService().getNextNode(a1, null, SkipType.PASS.getKey(),
            null, combine).getNodeCode());
        assertEquals(List.of("end"), FlowEngine.nodeService().getNextNodeList(
            combine.getAllNodes().stream().filter(n -> "joinP".equals(n.getNodeCode())).findFirst().orElseThrow(),
            null, SkipType.PASS.getKey(), Map.of(), null, combine).stream().map(Node::getNodeCode).toList());
        a1.setExt("[{\"code\":\"priority\",\"value\":\"high\"}]");
        assertEquals(Map.of("priority", "high"), FlowEngine.nodeService().getExt(a1));

        assertEquals(6, FlowEngine.skipService().getByDefId(definition.getId()).size());
        assertEquals(2, FlowEngine.skipService().getByDefIdAndNowNodeCode(definition.getId(), "forkP").size());
        assertEquals(6, FlowEngine.skipService().deleteSkipByDefIds(List.of(definition.getId())));
        assertEquals(0, harness.skipDao.size());
        assertEquals(6, FlowEngine.nodeService().deleteNodeByDefIds(List.of(definition.getId())));
        assertEquals(0, harness.nodeDao.size());
    }

    @Test
    void userApis_coverTaskConstructionUpdateAndCleanup() {
        Task task = FlowEngine.newTask().setId(1001L).setPermissionList(List.of("zhangsan", "lisi"));
        List<User> users = FlowEngine.userService().taskAddUser(task);
        assertEquals(2, users.size());
        assertEquals(2, task.getUserList().size());
        assertEquals(2, FlowEngine.userService().taskAddUsers(List.of(task)).size());

        assertTrue(FlowEngine.userService().updatePermission(task.getId(), List.of("wangwu"),
            UserType.APPROVAL.getKey(), true, "operator"));
        assertEquals(1, harness.userDao.size());
        assertEquals("wangwu", harness.userDao.all().get(0).getProcessedBy());
        assertEquals("operator", harness.userDao.all().get(0).getCreateBy());

        assertTrue(FlowEngine.userService().getByAssociateds(List.of(task.getId()), UserType.APPROVAL.getKey()).size() == 1);
        assertEquals(2, FlowEngine.userService().structureUser(task.getId(), List.of("a", "b"),
            UserType.APPROVAL.getKey()).size());
        assertEquals("creator", FlowEngine.userService().structureUser(task.getId(), "a",
            UserType.APPROVAL.getKey(), "creator").getCreateBy());
        assertEquals("creator", FlowEngine.userService().structureUser(task.getId(), List.of("a"),
            UserType.APPROVAL.getKey(), "creator").get(0).getCreateBy());
        FlowEngine.userService().deleteByTaskIds(List.of(task.getId()));
        assertEquals(0, harness.userDao.size());
    }

    @Test
    void historyConstructionApis_populateOperationSpecificFields() {
        Task task = FlowEngine.newTask().setId(2001L).setInstanceId(3001L).setDefinitionId(4001L)
            .setNodeCode("apply").setNodeName("审批").setNodeType(NodeType.BETWEEN.getKey());
        Node next = FlowEngine.newNode().setNodeCode("end").setNodeName("结束").setNodeType(NodeType.END.getKey());
        WorkflowContext context = TestFlows.context("zhangsan");
        context.setMessage("ok");
        context.setVariables(new java.util.HashMap<>(Map.of("x", 1)));

        HisTask pass = FlowEngine.hisTaskService().setSkipInsHis(task, List.of(next), context, SkipType.PASS.getKey());
        HisTask single = FlowEngine.hisTaskService().setSkipHisTask(task, next, context, SkipType.PASS.getKey());
        HisTask cooperate = FlowEngine.hisTaskService().setCooperateHis(task, context, List.of("lisi"),
            CooperateType.TRANSFER.getKey());
        HisTask depute = FlowEngine.hisTaskService().setDeputeHisTask(task, context,
            FlowEngine.newUser().setCreateBy("lisi"), SkipType.PASS.getKey());
        HisTask sign = FlowEngine.hisTaskService().setSignHisTask(task, context, "100", true);
        List<HisTask> list = FlowEngine.hisTaskService().setSkipHisList(List.of(task), List.of(next), context,
            SkipType.PASS.getKey());

        assertEquals("end", pass.getTargetNodeCode());
        assertEquals("end", single.getTargetNodeCode());
        assertEquals("lisi", cooperate.getCollaborator());
        assertEquals(CooperateType.DEPUTE.getKey(), depute.getCooperateType());
        assertEquals(CooperateType.COUNTERSIGN.getKey(), sign.getCooperateType());
        assertEquals(1, list.size());
        assertEquals("{\"x\":1}", pass.getVariable());
    }

    @Test
    void genericServiceApis_coverCrudBatchCountExistsAndQueryBuilders() {
        Form a = FlowEngine.newForm().setFormCode("generic-a").setFormName("A")
            .setIsPublish(PublishStatus.PUBLISHED.getKey());
        Form b = FlowEngine.newForm().setFormCode("generic-b").setFormName("B")
            .setIsPublish(PublishStatus.UNPUBLISHED.getKey());
        assertTrue(FlowEngine.formService().save(a));
        assertTrue(FlowEngine.formService().save(b));
        assertEquals(2, FlowEngine.formService().getByIds(List.of(a.getId(), b.getId())).size());
        assertEquals(2, FlowEngine.formService().selectCount(FlowEngine.newForm()));
        assertTrue(FlowEngine.formService().exists(FlowEngine.newForm().setFormCode("generic-a")));
        assertEquals(a.getId(), FlowEngine.formService().getOne(FlowEngine.newForm().setFormCode("generic-a")).getId());
        assertTrue(FlowEngine.formService().updateById(a.setFormName("A2")));
        assertEquals("A2", harness.formDao.raw(a.getId()).getFormName());
        FlowEngine.formService().saveBatch(List.of(FlowEngine.newForm().setFormCode("batch-1"),
            FlowEngine.newForm().setFormCode("batch-2")), 1);
        assertEquals(4, harness.formDao.size());
        FlowEngine.formService().updateBatch(List.of(b.setFormName("B2")));
        assertEquals("B2", harness.formDao.raw(b.getId()).getFormName());
        assertNotNull(FlowEngine.formService().orderById().desc().list(FlowEngine.newForm()));
        assertNotNull(FlowEngine.formService().orderByCreateTime().list(FlowEngine.newForm()));
        assertNotNull(FlowEngine.formService().orderByUpdateTime().list(FlowEngine.newForm()));
        assertNotNull(FlowEngine.formService().orderByAsc("form_code").list(FlowEngine.newForm()));
        assertNotNull(FlowEngine.formService().orderByDesc("form_code").list(FlowEngine.newForm()));
        assertNotNull(FlowEngine.formService().orderBy("form_code").list(FlowEngine.newForm()));
        assertTrue(FlowEngine.formService().removeById(a.getId()));
        assertTrue(FlowEngine.formService().remove(FlowEngine.newForm().setFormCode("generic-b")));
        Form c = FlowEngine.newForm().setFormCode("generic-c");
        FlowEngine.formService().save(c);
        assertTrue(FlowEngine.formService().removeByIds(List.of(c.getId())));
    }

    @Test
    void formPublishedPage_usesPageContract() {
        Form form = FlowEngine.newForm().setFormCode("page-a").setFormName("页面")
            .setIsPublish(PublishStatus.PUBLISHED.getKey());
        FlowEngine.formService().save(form);
        Page<Form> page = FlowEngine.formService().publishedPage("页面", 1, 10);
        assertEquals(1, page.getTotal());
        assertEquals(1, page.getList().size());
        assertTrue(FlowEngine.formService().unPublish(form.getId()));
        assertEquals(PublishStatus.UNPUBLISHED.getKey(), harness.formDao.raw(form.getId()).getIsPublish());
    }

    @Test
    void definitionQueryApis_coverCodeStatusAndCombineLookups() {
        var first = TestFlows.serialFlow("api-def", "1", PublishStatus.PUBLISHED.getKey());
        var second = TestFlows.serialFlow("api-def", "2", PublishStatus.UNPUBLISHED.getKey());

        assertEquals(2, FlowEngine.defService().queryByCodeList(List.of("api-def")).size());
        assertEquals(2, FlowEngine.defService().getByFlowCode("api-def").size());
        assertEquals(first.getId(), FlowEngine.defService().getPublishByFlowCode("api-def").getId());
        assertEquals(3, FlowEngine.defService().getFlowCombineNoDef(first.getId()).getAllNodes().size());
        assertEquals(first.getId(), FlowEngine.defService().getFlowCombine(first.getId()).getDefinition().getId());
        assertEquals(first.getId(), FlowEngine.defService().getFlowCombine(first).getDefinition().getId());

        FlowEngine.defService().updatePublishStatus(List.of(second.getId()), PublishStatus.EXPIRED.getKey());
        assertEquals(PublishStatus.EXPIRED.getKey(), harness.defDao.raw(second.getId()).getIsPublish());
    }

    @Test
    void taskConstructionQueryAndVariableApis_keepPublicContracts() {
        Definition definition = TestFlows.serialFlow("api-task");
        Instance instance = FlowEngine.newIns().setId(9001L).setDefinitionId(definition.getId())
            .setVariable("{\"old\":1}");
        Node apply = FlowEngine.nodeService().getByDefIdAndNodeCode(definition.getId(), "apply");
        Task task = FlowEngine.taskService().addTask(apply, instance, definition,
            TestFlows.context("zhangsan"), SkipType.PASS.getKey());
        FlowEngine.taskService().save(task);

        assertEquals(1, FlowEngine.taskService().getByInsId(instance.getId()).size());
        assertEquals(1, FlowEngine.taskService().getByInsIdAndNodeCodes(instance.getId(), List.of("apply")).size());
        FlowEngine.taskService().mergeVariable(instance, Map.of("next", 2));
        assertEquals(Map.of("old", 1, "next", 2), instance.getVariableMap());

        Task end = FlowEngine.newTask().setId(9999L).setNodeCode("end").setNodeName("结束")
            .setNodeType(NodeType.END.getKey()).setFlowStatus("8");
        List<Task> addTasks = new java.util.ArrayList<>(List.of(task, end));
        FlowEngine.taskService().setInsFinishInfo(instance, addTasks, new HashMap<>(Map.of("done", true)));
        assertEquals("end", instance.getNodeCode());
        assertEquals(1, addTasks.size(), "结束任务会从待保存任务集合中移除");
        assertEquals(true, instance.getVariableMap().get("done"));
    }
}

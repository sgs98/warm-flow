package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.dto.NodeJson;
import org.dromara.warm.flow.core.dto.SkipJson;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流程定义生命周期特征测试：发布/失效/未发布状态迁移（含已使用兄弟版本改失效）、
 * 挂起激活、删除级联、复制版本递增、版本号生成（数字/非数字分支）、
 * 设计器读取与 saveDef 保存校验。
 *
 * @author warm
 */
class DefLifecycleCharacteristicTest {

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
    void publish_withoutNodes_throwsNotDrawn() {
        Definition def = FlowEngine.newDef().setFlowCode("dl1").setFlowName("空图")
                .setVersion("1").setIsPublish(PublishStatus.PUBLISHED.getKey())
                .setActivityStatus(ActivityStatus.ACTIVITY.getKey());
        FlowEngine.defService().save(def);

        FlowException ex = assertThrows(FlowException.class,
                () -> FlowEngine.defService().publish(def.getId()));
        assertEquals(ExceptionCons.NOT_DRAW_FLOW_ERROR, ex.getMessage());
    }

    @Test
    void publish_supersedesUnusedSibling_toUnpublished() {
        Definition v1 = TestFlows.serialFlow("dl2", "1", PublishStatus.PUBLISHED.getKey());
        Definition v2 = TestFlows.serialFlow("dl2", "2", PublishStatus.UNPUBLISHED.getKey());

        assertTrue(FlowEngine.defService().publish(v2.getId()));

        // 已发布未使用的兄弟版本 → 未发布；新版本 → 已发布
        assertEquals(PublishStatus.UNPUBLISHED.getKey(), harness.defDao.raw(v1.getId()).getIsPublish());
        assertEquals(PublishStatus.PUBLISHED.getKey(), harness.defDao.raw(v2.getId()).getIsPublish());
    }

    @Test
    void publish_supersedesUsedSibling_toExpired() {
        Definition v1 = TestFlows.serialFlow("dl3", "1", PublishStatus.PUBLISHED.getKey());
        Definition v2 = TestFlows.serialFlow("dl3", "2", PublishStatus.UNPUBLISHED.getKey());
        // 发起实例挂在已发布的 v1 上（getPublishByFlowCode 只命中 v1）
        FlowEngine.insService().start("biz-dl3", "dl3", TestFlows.context(TestFlows.HANDLER));

        assertTrue(FlowEngine.defService().publish(v2.getId()));

        // 已发布已使用的兄弟版本 → 失效
        assertEquals(PublishStatus.EXPIRED.getKey(), harness.defDao.raw(v1.getId()).getIsPublish());
        assertEquals(PublishStatus.PUBLISHED.getKey(), harness.defDao.raw(v2.getId()).getIsPublish());
    }

    @Test
    void unPublish_guardsAndSetsStatus() {
        Definition def = TestFlows.serialFlow("dl4", "1", PublishStatus.PUBLISHED.getKey());
        FlowEngine.insService().start("biz-dl4", "dl4", TestFlows.context(TestFlows.HANDLER));

        FlowException guarded = assertThrows(FlowException.class,
                () -> FlowEngine.defService().unPublish(def.getId()));
        assertEquals(ExceptionCons.EXIST_START_TASK, guarded.getMessage());

        // 无实例的版本可取消发布
        Definition fresh = TestFlows.serialFlow("dl4b", "1", PublishStatus.PUBLISHED.getKey());
        assertTrue(FlowEngine.defService().unPublish(fresh.getId()));
        assertEquals(PublishStatus.UNPUBLISHED.getKey(), harness.defDao.raw(fresh.getId()).getIsPublish());
    }

    @Test
    void unActive_thenActive_roundTripWithGuards() {
        Definition def = TestFlows.serialFlow("dl5", "1", PublishStatus.PUBLISHED.getKey());

        assertTrue(FlowEngine.defService().unActive(def.getId()));
        assertEquals(ActivityStatus.SUSPENDED.getKey(), harness.defDao.raw(def.getId()).getActivityStatus());
        FlowException again = assertThrows(FlowException.class,
                () -> FlowEngine.defService().unActive(def.getId()));
        assertEquals(ExceptionCons.DEFINITION_ALREADY_SUSPENDED, again.getMessage());

        assertTrue(FlowEngine.defService().active(def.getId()));
        assertEquals(ActivityStatus.ACTIVITY.getKey(), harness.defDao.raw(def.getId()).getActivityStatus());
        FlowException reActive = assertThrows(FlowException.class,
                () -> FlowEngine.defService().active(def.getId()));
        assertEquals(ExceptionCons.DEFINITION_ALREADY_ACTIVITY, reActive.getMessage());

        FlowException unknown = assertThrows(FlowException.class,
                () -> FlowEngine.defService().active(-1L));
        assertEquals(ExceptionCons.NOT_FOUNT_DEF, unknown.getMessage());
    }

    @Test
    void removeDef_guardsAndCascades() {
        Definition used = TestFlows.serialFlow("dl6", "1", PublishStatus.PUBLISHED.getKey());
        FlowEngine.insService().start("biz-dl6", "dl6", TestFlows.context(TestFlows.HANDLER));
        FlowException guarded = assertThrows(FlowException.class,
                () -> FlowEngine.defService().removeDef(List.of(used.getId())));
        assertEquals(ExceptionCons.EXIST_START_TASK, guarded.getMessage());

        Definition fresh = TestFlows.serialFlow("dl6b", "1", PublishStatus.PUBLISHED.getKey());
        assertTrue(FlowEngine.defService().removeDef(List.of(fresh.getId())));
        assertEquals(1, harness.defDao.size(), "仅删除目标定义");
        assertEquals(0, harness.nodeDao.all().stream()
                .filter(n -> n.getDefinitionId().equals(fresh.getId())).count(), "节点级联删除");
        assertEquals(0, harness.skipDao.all().stream()
                .filter(s -> s.getDefinitionId().equals(fresh.getId())).count(), "连线级联删除");
        assertNull(harness.defDao.raw(fresh.getId()));
    }

    @Test
    void copyDef_duplicatesGraphWithNextVersion() {
        Definition v1 = TestFlows.serialFlow("dl7", "1", PublishStatus.PUBLISHED.getKey());
        int nodeCount = harness.nodeDao.size();
        int skipCount = harness.skipDao.size();

        assertTrue(FlowEngine.defService().copyDef(v1.getId()));

        assertEquals(2, harness.defDao.size());
        Definition v2 = harness.defDao.all().stream()
                .filter(d -> !d.getId().equals(v1.getId())).findFirst().orElseThrow();
        assertEquals("2", v2.getVersion(), "复制版本取同编码最大版本 +1");
        assertEquals(nodeCount * 2, harness.nodeDao.size(), "节点图完整复制");
        assertEquals(skipCount * 2, harness.skipDao.size(), "连线完整复制");
        assertTrue(harness.nodeDao.all().stream()
                .anyMatch(n -> n.getDefinitionId().equals(v2.getId())), "新节点挂在新定义下");
        assertEquals("dl7", v2.getFlowCode());
    }

    @Test
    void copyDef_unknownId_throwsNpe_documentingDeadCheck() {
        // 已知缺陷（设计文档附录B）：copyDef 先解引用 getById(id).copy() 再断言，
        // 不存在的 id 抛 NPE 而非 NOT_FOUNT_DEF——锁定现状，修复时翻转此断言
        assertThrows(NullPointerException.class, () -> FlowEngine.defService().copyDef(-1L));
    }

    @Test
    void insertFlow_assignsNumericAndTimestampVersions() {
        // 全新编码：无历史版本 → 1，再次插入 → 2
        Definition first = FlowEngine.defService().insertFlow(
                FlowEngine.newDef().setFlowCode("dl9").setFlowName("版本一")
                , List.of(), List.of());
        assertEquals("1", first.getVersion());
        Definition second = FlowEngine.defService().insertFlow(
                FlowEngine.newDef().setFlowCode("dl9").setFlowName("版本二")
                , List.of(), List.of());
        assertEquals("2", second.getVersion());

        // 非数字版本：最新时间戳的非数字版本追加 _1
        FlowEngine.defService().save(FlowEngine.newDef().setFlowCode("dl9b").setFlowName("旧版")
                .setVersion("abc").setCreateTime(new Date()));
        Definition appended = FlowEngine.defService().insertFlow(
                FlowEngine.newDef().setFlowCode("dl9b").setFlowName("新版")
                , List.of(), List.of());
        assertEquals("abc_1", appended.getVersion());
    }

    @Test
    void getAllDataDefinition_queryDesign_exportJson_roundTrip() {
        Definition def = TestFlows.serialFlow("dl10", "1", PublishStatus.PUBLISHED.getKey());

        // 全量数据：节点 + 按 nowNodeCode 分组的连线
        Definition all = FlowEngine.defService().getAllDataDefinition(def.getId());
        assertEquals(3, all.getNodeList().size());
        Node apply = all.getNodeList().stream()
                .filter(n -> "apply".equals(n.getNodeCode())).findFirst().orElseThrow();
        assertEquals(1, apply.getSkipList().size());

        // 设计器视图
        DefJson design = FlowEngine.defService().queryDesign(def.getId());
        assertEquals("dl10", design.getFlowCode());
        assertEquals(3, design.getNodeList().size());

        // 导出 json（不含发布状态）→ 重新导入生成新版本定义
        String json = FlowEngine.defService().exportJson(def.getId());
        assertTrue(json.contains("dl10"));
        Definition imported = FlowEngine.defService().importJson(json);
        assertNotNull(imported.getId());
        assertEquals("2", imported.getVersion(), "同编码导入版本递增");
        assertEquals(3, harness.nodeDao.all().stream()
                .filter(n -> n.getDefinitionId().equals(imported.getId())).count());
        assertEquals(2, harness.skipDao.all().stream()
                .filter(s -> s.getDefinitionId().equals(imported.getId())).count());

        Definition streamImported = FlowEngine.defService().importIs(
            new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        assertNotNull(streamImported.getId());
        assertEquals("3", streamImported.getVersion(), "InputStream 导入沿用 JSON 导入版本规则");
    }

    @Test
    void saveDef_newGraph_persistsWithValidation() throws Exception {
        FlowEngine.defService().saveDef(threeNodeJson("sd1", "设计器流程"), false);

        assertEquals(1, harness.defDao.size());
        Definition saved = harness.defDao.all().get(0);
        assertEquals("sd1", saved.getFlowCode());
        assertEquals("1", saved.getVersion(), "新增定义自动分配版本");
        assertEquals(3, harness.nodeDao.size());
        assertEquals(2, harness.skipDao.size());

        // 缺少开始节点 → 报错（消息带流程名前缀）
        DefJson noStart = threeNodeJson("sd2", "缺开始");
        noStart.getNodeList().removeIf(n -> NodeType.START.getKey().equals(n.getNodeType()));
        FlowException lostStart = assertThrows(FlowException.class,
                () -> FlowEngine.defService().saveDef(noStart, false));
        assertTrue(lostStart.getMessage().contains(ExceptionCons.LOST_START_NODE)
                , "实际消息: " + lostStart.getMessage());

        // 连线起点不属于任何节点 → 无用连线报错
        DefJson useless = threeNodeJson("sd3", "无用连线");
        useless.getNodeList().get(0).getSkipList().get(0).setNowNodeCode("ghost");
        FlowException uselessSkip = assertThrows(FlowException.class,
                () -> FlowEngine.defService().saveDef(useless, false));
        assertTrue(uselessSkip.getMessage().contains(ExceptionCons.FLOW_HAVE_USELESS_SKIP)
                , "实际消息: " + uselessSkip.getMessage());
    }

    @Test
    void saveDef_updatePath_replacesGraph_onlyNodeSkipKeepsDefinition() throws Exception {
        FlowEngine.defService().saveDef(threeNodeJson("sd4", "初版"), false);
        Definition saved = harness.defDao.all().get(0);
        Long defId = saved.getId();
        List<Long> oldNodeIds = harness.nodeDao.all().stream()
                .filter(n -> n.getDefinitionId().equals(defId)).map(Node::getId).toList();

        // 全量更新：定义字段与节点连线一起替换
        DefJson updated = threeNodeJson("sd4", "改名版");
        updated.setId(defId);
        FlowEngine.defService().saveDef(updated, false);
        assertEquals("改名版", harness.defDao.raw(defId).getFlowName());
        assertEquals(3, harness.nodeDao.size(), "节点删除后重建");
        assertTrue(harness.nodeDao.all().stream()
                .filter(n -> n.getDefinitionId().equals(defId))
                .noneMatch(n -> oldNodeIds.contains(n.getId())), "节点 id 全部更换");

        // onlyNodeSkip=true：仅替换节点连线，定义字段不动
        DefJson nodesOnly = threeNodeJson("sd4", "不应写入");
        nodesOnly.setId(defId);
        nodesOnly.setFlowCode("sd4-changed");
        FlowEngine.defService().saveDef(nodesOnly, true);
        assertEquals("改名版", harness.defDao.raw(defId).getFlowName(), "定义字段不被更新");
        assertEquals("sd4", harness.defDao.raw(defId).getFlowCode());
    }

    /** 构造 start → apply → end 的设计器 json（apply 带办理人） */
    private DefJson threeNodeJson(String flowCode, String flowName) {
        DefJson json = new DefJson().setFlowCode(flowCode).setFlowName(flowName);
        NodeJson start = new NodeJson().setNodeType(NodeType.START.getKey()).setNodeCode("start");
        start.getSkipList().add(new SkipJson()
                .setNowNodeCode("start").setNextNodeCode("apply").setSkipType("PASS"));
        NodeJson apply = new NodeJson().setNodeType(NodeType.BETWEEN.getKey())
                .setNodeCode("apply").setPermissionFlag(TestFlows.HANDLER);
        apply.getSkipList().add(new SkipJson()
                .setNowNodeCode("apply").setNextNodeCode("end").setSkipType("PASS"));
        NodeJson end = new NodeJson().setNodeType(NodeType.END.getKey()).setNodeCode("end");
        json.getNodeList().add(start);
        json.getNodeList().add(apply);
        json.getNodeList().add(end);
        return json;
    }
}

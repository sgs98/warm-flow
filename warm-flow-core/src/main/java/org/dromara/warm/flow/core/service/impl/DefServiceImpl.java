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

import lombok.extern.slf4j.Slf4j;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.EventCons;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.dto.FlowCombine;
import org.dromara.warm.flow.core.dto.PathWayData;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Skip;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.EventType;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.orm.dao.FlowDefinitionDao;
import org.dromara.warm.flow.core.orm.service.impl.WarmServiceImpl;
import org.dromara.warm.flow.core.service.DefService;
import org.dromara.warm.flow.core.service.event.EventConfig;
import org.dromara.warm.flow.core.timer.TimerContext;
import org.dromara.warm.flow.core.timer.TimerScheduleStrategies;
import org.dromara.warm.flow.core.utils.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 流程定义服务实现。
 *
 * <p>负责流程设计的导入导出、结构校验、版本管理、发布状态切换，以及定义、节点、连线三类数据的协同持久化。</p>
 *
 * @author warm
 * @since 2023-03-29
 */
@Slf4j
public class DefServiceImpl extends WarmServiceImpl<FlowDefinitionDao<Definition>, Definition> implements DefService {

    /**
     * 注入流程定义 DAO。
     *
     * @param warmDao 流程定义数据访问对象
     * @return 当前服务实例
     */
    @Override
    public DefService setDao(FlowDefinitionDao<Definition> warmDao) {
        this.warmDao = warmDao;
        return this;
    }

    /**
     * 从输入流读取流程设计 JSON 并导入。
     *
     * @param is 流程设计输入流
     * @return 新建的流程定义
     */
    @Override
    public Definition importIs(InputStream is) {
        StringBuilder stringBuilder = new StringBuilder();
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
                stringBuilder.append(System.lineSeparator());
            }
        } catch (IOException e) {
            throw new FlowException(ExceptionCons.READ_IS_ERROR);
        }
        return importJson(stringBuilder.toString());
    }

    /**
     * 将流程设计 JSON 反序列化后导入。
     *
     * @param defJson 流程设计 JSON
     * @return 新建的流程定义
     */
    @Override
    public Definition importJson(String defJson) {
        return importDef(FlowEngine.jsonConvert.strToBean(defJson, DefJson.class));
    }

    /**
     * 将设计 DTO 转换为定义、节点和连线，并以新版本写入。
     *
     * @param defJson 流程设计 DTO
     * @return 新建的流程定义
     */
    @Override
    public Definition importDef(DefJson defJson) {
        Definition definition = DefJson.copyDef(defJson);
        FlowCombine flowCombine = FlowConfigUtil.structureFlow(definition);
        return insertFlow(flowCombine.getDefinition(), flowCombine.getAllNodes(), flowCombine.getAllSkips());
    }

    /**
     * 为流程定义生成新版本并依次保存定义、节点和连线。
     *
     * <p>调用方应保证节点、连线中的定义主键已经与当前定义一致。</p>
     *
     * @param definition 流程定义
     * @param nodeList   流程节点集合
     * @param skipList   流程连线集合
     * @return 已保存的流程定义
     */
    @Override
    public Definition insertFlow(Definition definition, List<Node> nodeList, List<Skip> skipList) {
        definition.setVersion(getNewVersion(definition));
        FlowEngine.defService().save(definition);
        saveGraph(nodeList, skipList);
        return definition;
    }

    /**
     * 保存流程定义前生成同编码下的新版本号。
     *
     * @param definition 待保存流程定义
     * @return 是否保存成功
     */
    @Override
    public boolean checkAndSave(Definition definition) {
        return save(definition.setVersion(getNewVersion(definition)));
    }

    /**
     * 保存设计器提交的完整流程结构。
     *
     * <p>新增时生成主键和版本；仅未发布定义可更新节点和连线。{@code onlyNodeSkip} 为真时保留定义主表字段。</p>
     *
     * @param defJson      设计器提交的流程结构
     * @param onlyNodeSkip 是否只更新节点和连线
     */
    @Override
    public void saveDef(DefJson defJson, boolean onlyNodeSkip) {
        if (ObjectUtil.isNull(defJson)) {
            return;
        }
        FlowCombine flowCombine = DefJson.copyCombine(defJson);
        Definition definition = flowCombine.getDefinition();
        Long id = definition.getId();
        if (id != null) {
            Definition existing = getById(id);
            AssertUtil.isNull(existing, ExceptionCons.NOT_FOUNT_DEF);
            AssertUtil.isTrue(!PublishStatus.UNPUBLISHED.getKey().equals(existing.getIsPublish()),
                ExceptionCons.PUBLISHED_DEF_NOT_EDITABLE);
            if (onlyNodeSkip) {
                definition.setFlowCode(existing.getFlowCode()).setFlowName(existing.getFlowName());
            }
            // ext 归业务系统：入参未携带时保留库中已有值，避免保存流程结构时清空业务扩展数据
            if (ObjectUtil.isNull(definition.getExt())) {
                definition.setExt(existing.getExt());
            }
        }
        // 如果是新增的流程定义
        if (ObjectUtil.isNull(id)) {
            definition.setVersion(getNewVersion(definition));
            FlowEngine.dataFillHandler().idFill(definition);
        }

        // 校验流程定义合法性
        checkFlowLegal(flowCombine);

        // 如果是新增的流程定义
        if (ObjectUtil.isNull(id)) {
            FlowEngine.defService().save(definition);
        } else {
            if (!onlyNodeSkip) {
                FlowEngine.defService().updateById(definition);
            }
            removeGraph(id);
        }

        List<Node> allNodes = flowCombine.getAllNodes();
        normalizeNodeRatios(allNodes);
        saveGraph(allNodes, flowCombine.getAllSkips());
    }

    /**
     * 导出流程设计 JSON，发布状态不纳入导出内容。
     *
     * @param id 流程定义主键
     * @return 流程设计 JSON
     */
    @Override
    public String exportJson(Long id) {
        return FlowEngine.jsonConvert.objToStr(queryDesign(id).setIsPublish(null));
    }

    /**
     * 查询流程定义及其节点、连线，并把出口连线挂载到对应节点。
     *
     * @param id 流程定义主键
     * @return 包含节点和连线的流程定义
     */
    @Override
    public Definition getAllDataDefinition(Long id) {
        Definition definition = getDao().selectById(id);
        AssertUtil.isNull(definition, ExceptionCons.NOT_FOUNT_DEF);
        List<Node> nodeList = FlowEngine.nodeService().getByDefId(id);
        definition.setNodeList(nodeList);
        List<Skip> skips = FlowEngine.skipService().getByDefId(id);
        Map<String, List<Skip>> flowSkipMap = skips.stream()
            .collect(Collectors.groupingBy(Skip::getNowNodeCode));
        nodeList.forEach(flowNode -> flowNode.setSkipList(flowSkipMap.get(flowNode.getNodeCode())));
        return definition;
    }

    /**
     * 查询定义、节点和连线组成的完整运行结构。
     *
     * @param id 流程定义主键
     * @return 完整流程组合数据
     */
    @Override
    public FlowCombine getFlowCombine(Long id) {
        return getFlowCombine(getDao().selectById(id));
    }

    /**
     * 查询节点和连线组成的运行结构，不加载定义主表。
     *
     * @param id 流程定义主键
     * @return 不含定义主表的流程组合数据
     */
    @Override
    public FlowCombine getFlowCombineNoDef(Long id) {
        FlowCombine flowCombine = new FlowCombine();
        flowCombine.setAllNodes(FlowEngine.nodeService().getByDefId(id));
        flowCombine.setAllSkips(FlowEngine.skipService().getByDefId(id));
        return flowCombine;
    }

    /**
     * 将给定定义与其节点、连线组装为运行结构。
     *
     * @param definition 流程定义
     * @return 完整流程组合数据
     */
    @Override
    public FlowCombine getFlowCombine(Definition definition) {
        AssertUtil.isNull(definition, ExceptionCons.NOT_FOUNT_DEF);
        FlowCombine flowCombine = getFlowCombineNoDef(definition.getId());
        flowCombine.setDefinition(definition);
        return flowCombine;
    }

    /**
     * 查询设计器使用的树形流程结构。
     *
     * @param id 流程定义主键
     * @return 设计器流程结构
     */
    @Override
    public DefJson queryDesign(Long id) {
        return DefJson.copyDef(getAllDataDefinition(id));
    }

    /**
     * 按流程编码集合批量查询定义及历史版本。
     *
     * @param flowCodeList 流程编码集合
     * @return 命中的流程定义
     */
    @Override
    public List<Definition> queryByCodeList(List<String> flowCodeList) {
        return getDao().queryByCodeList(flowCodeList);
    }

    /**
     * 批量更新流程定义发布状态。
     *
     * @param ids           流程定义主键集合
     * @param publishStatus 目标发布状态
     */
    @Override
    public void updatePublishStatus(List<Long> ids, Integer publishStatus) {
        getDao().updatePublishStatus(ids, publishStatus);
    }

    /**
     * 删除流程定义及其节点、连线、等待记录。
     *
     * <p>任一待删除定义已经产生流程实例时拒绝删除，以保护运行和审计数据。</p>
     *
     * @param ids 流程定义主键集合
     * @return 是否删除成功
     */
    @Override
    public boolean removeDef(List<Long> ids) {
        ids.forEach(id -> {
            List<Instance> instances = FlowEngine.insService().getByDefId(id);
            AssertUtil.isNotEmpty(instances, ExceptionCons.EXIST_START_TASK);
            // 定义删除时同步删除其等待记录：已保证无流程实例，故此处均为定义级定时 / 消息启动等待，
            // 随定义一并清理（逻辑 / 物理跟随引擎删除模式），不留指向已删定义的孤儿等待
            FlowEngine.eventWaitService().remove(FlowEngine.newEventWait().setDefinitionId(id));
        });
        FlowEngine.nodeService().deleteNodeByDefIds(ids);
        FlowEngine.skipService().deleteSkipByDefIds(ids);
        return removeByIds(ids);
    }

    /**
     * 发布指定版本，并调整同流程编码下其他已发布版本的状态。
     *
     * <p>发布前校验持久化流程图的结构合法性；已被实例使用的旧版本转为失效，
     * 未被使用的旧版本退回未发布，确保同一流程编码只有一个当前发布版本。</p>
     *
     * @param id 待发布定义主键
     * @return 是否更新成功
     */
    @Override
    public boolean publish(Long id) {
        Definition definition = getById(id);
        AssertUtil.isNull(definition, ExceptionCons.NOT_FOUNT_DEF);
        FlowCombine combine = getFlowCombine(definition);
        AssertUtil.isEmpty(combine.getAllNodes(), ExceptionCons.NOT_DRAW_FLOW_ERROR);
        checkFlowLegal(combine);
        // 发布能力门禁：未开放运行时的事件类型只能保存为未发布草稿
        FlowConfigUtil.validateEventCapability(combine);
        List<Definition> definitions = getByFlowCode(definition.getFlowCode());
        // 已发布流程定义，改为已失效或者未发布状态
        List<Long> otherDefIds = definitions.stream()
            .filter(item -> !Objects.equals(definition.getId(), item.getId())
                && PublishStatus.PUBLISHED.getKey().equals(item.getIsPublish()))
            .map(Definition::getId)
            .collect(Collectors.toList());
        if (CollUtil.isNotEmpty(otherDefIds)) {
            List<Instance> instanceList = FlowEngine.insService().listByDefIds(otherDefIds);
            if (CollUtil.isNotEmpty(instanceList)) {
                // 已发布已使用过的流程定义
                Set<Long> useDefIds = StreamUtils.toSet(instanceList, Instance::getDefinitionId);
                if (CollUtil.isNotEmpty(useDefIds)) {
                    // 已发布已使用过的流程定义，改为已失效
                    updatePublishStatus(new ArrayList<>(useDefIds), PublishStatus.EXPIRED.getKey());
                    // 过滤掉已发布已使用-->已发布未使用
                    otherDefIds.removeIf(useDefIds::contains);
                }
            }
            if (CollUtil.isNotEmpty(otherDefIds)) {
                // 已发布未使用过的流程定义，改为未发布
                updatePublishStatus(otherDefIds, PublishStatus.UNPUBLISHED.getKey());
            }
        }

        Definition flowDefinition = FlowEngine.newDef();
        flowDefinition.setId(id);
        flowDefinition.setIsPublish(PublishStatus.PUBLISHED.getKey());
        boolean updated = updateById(flowDefinition);
        if (updated) {
            // 发布钩子：取消旧版本定义级等待，为本版本重建 TIMER_START 等待
            if (CollUtil.isNotEmpty(otherDefIds)) {
                for (Long otherDefId : otherDefIds) {
                    FlowEngine.eventWaitService().cancelByDefinitionId(otherDefId);
                }
            }
            syncTimerStartWait(getById(id), combine);
        }
        return updated;
    }

    /**
     * 同步定义级 TIMER_START 等待记录：定义发布且激活、固定 START 的直接后继为
     * TIMER_START 事件节点时创建（同一定义只保留一条，幂等）；否则取消已有记录。
     *
     * @param definition 流程定义（须为最新持久化状态）
     * @param combine    定义图
     */
    private void syncTimerStartWait(Definition definition, FlowCombine combine) {
        // 先取消本定义已有的定义级等待，保证发布 / 重新发布幂等
        FlowEngine.eventWaitService().cancelByDefinitionId(definition.getId());
        if (!PublishStatus.PUBLISHED.getKey().equals(definition.getIsPublish())
            || ActivityStatus.isSuspended(definition.getActivityStatus())) {
            return;
        }
        Node startNode = StreamUtils.filterOne(combine.getAllNodes()
            , node -> NodeType.isStart(node.getNodeType()));
        if (startNode == null) {
            return;
        }
        List<Node> successors = FlowEngine.nodeService().getNextNodeList(startNode, null
            , SkipType.PASS.getKey(), null, new PathWayData(), combine);
        if (CollUtil.isEmpty(successors) || !EventType.TIMER_START.getValue()
            .equals(successors.get(0).getEventType())) {
            return;
        }
        Node eventNode = successors.get(0);
        EventConfig config = EventConfig.parse(eventNode.getNodeConfig());
        AssertUtil.isTrue(!config.hasTimer(), ExceptionCons.TIMER_EVENT_NO_CONFIG);
        Date now = new Date();
        EventWait wait = FlowEngine.newEventWait();
        FlowEngine.dataFillHandler().idFill(wait);
        Date nextTriggerAt = TimerScheduleStrategies.nextTriggerAt(config.getTimerType()
            , new TimerContext()
                .setPreviousTriggerAt(null)
                .setCurrentTime(now)
                .setTriggerInterval(config.getTimerValue())
                .setTimezone(config.getTimezone())
                .setRepeatCount(0));
        wait.setDefinitionId(definition.getId())
            .setNodeCode(eventNode.getNodeCode())
            .setEventType(EventType.TIMER_START.getValue())
            .setStatus(EventCons.STATUS_WAITING)
            .setRetryCount(0)
            .setRepeatCount(0)
            .setTriggerType(config.getTimerType())
            .setTriggerInterval(config.getTimerValue())
            .setTimezone(config.getTimezone())
            .setMaxRepeatCount(config.getMaxCount())
            .setNextTriggerAt(nextTriggerAt)
            .setVersion(0)
            .setCreateTime(now)
            .setUpdateTime(now)
            .setTenantId(definition.getTenantId());
        // 节点默认变量随等待记录落库：触发时合并为实例初始变量，重试 / 租约恢复后仍可读取
        Map<String, Object> nodeVariables = NodeDefaults.of(eventNode);
        if (MapUtil.isNotEmpty(nodeVariables)) {
            wait.setPayload(FlowEngine.jsonConvert.objToStr(nodeVariables));
        }
        FlowEngine.eventWaitService().save(wait);
        log.info("流程定义 {} 发布，已创建定时启动等待 node={} nextTriggerAt={}"
            , definition.getId(), eventNode.getNodeCode(), nextTriggerAt);
    }

    /**
     * 取消发布未被流程实例使用的定义。
     *
     * @param id 流程定义主键
     * @return 是否更新成功
     */
    @Override
    public boolean unPublish(Long id) {
        AssertUtil.isNull(getById(id), ExceptionCons.NOT_FOUNT_DEF);
        List<Instance> instances = FlowEngine.insService().getByDefId(id);
        AssertUtil.isNotEmpty(instances, ExceptionCons.EXIST_START_TASK);
        Definition definition = FlowEngine.newDef().setId(id);
        definition.setIsPublish(PublishStatus.UNPUBLISHED.getKey());
        boolean updated = updateById(definition);
        if (updated) {
            // 取消发布后定义级定时启动等待不再触发
            FlowEngine.eventWaitService().cancelByDefinitionId(id);
        }
        return updated;
    }

    /**
     * 复制流程定义及其节点、连线，生成同编码下的新版本。
     *
     * @param id 源流程定义主键
     * @return 是否复制成功
     */
    @Override
    public boolean copyDef(Long id) {
        return copyDefReturnId(id) != null;
    }

    /**
     * 复制流程定义及其节点、连线，生成同编码下的新版本，并返回新定义主键。
     *
     * @param id 源流程定义主键
     * @return 新定义主键，保存失败时为 null
     */
    @Override
    public Long copyDefReturnId(Long id) {
        Definition source = getById(id);
        AssertUtil.isNull(source, ExceptionCons.NOT_FOUNT_DEF);
        Definition definition = source.copy();
        definition.setVersion(getNewVersion(definition));
        // 复制产物固定为未发布草稿，不依赖数据库默认值
        definition.setIsPublish(PublishStatus.UNPUBLISHED.getKey());

        List<Node> nodeList = FlowEngine.nodeService().getByDefId(id).stream().map(Node::copy).collect(Collectors.toList());
        List<Skip> skipList = FlowEngine.skipService().getByDefId(id).stream().map(Skip::copy).collect(Collectors.toList());
        FlowEngine.dataFillHandler().idFill(definition);

        nodeList.forEach(node -> node.setDefinitionId(definition.getId()));
        skipList.forEach(skip -> skip.setDefinitionId(definition.getId()));
        saveGraph(nodeList, skipList);
        return save(definition) ? definition.getId() : null;
    }

    /**
     * 激活已挂起的流程定义。
     *
     * @param id 流程定义主键
     * @return 是否更新成功
     */
    @Override
    public boolean active(Long id) {
        Definition definition = getById(id);
        AssertUtil.isNull(definition, ExceptionCons.NOT_FOUNT_DEF);
        AssertUtil.isTrue(ActivityStatus.isActivity(definition.getActivityStatus()), ExceptionCons.DEFINITION_ALREADY_ACTIVITY);
        definition.setActivityStatus(ActivityStatus.ACTIVITY.getKey());
        boolean updated = updateById(definition);
        if (updated) {
            // 挂起定义重新激活：若仍已发布，重建定时启动等待并从激活时间计算后续计划
            if (PublishStatus.PUBLISHED.getKey().equals(definition.getIsPublish())) {
                syncTimerStartWait(definition, getFlowCombine(definition));
            }
        }
        return updated;
    }

    /**
     * 挂起活动中的流程定义。
     *
     * @param id 流程定义主键
     * @return 是否更新成功
     */
    @Override
    public boolean unActive(Long id) {
        Definition definition = getById(id);
        AssertUtil.isNull(definition, ExceptionCons.NOT_FOUNT_DEF);
        AssertUtil.isTrue(ActivityStatus.isSuspended(definition.getActivityStatus()), ExceptionCons.DEFINITION_ALREADY_SUSPENDED);
        definition.setActivityStatus(ActivityStatus.SUSPENDED.getKey());
        boolean updated = updateById(definition);
        if (updated) {
            // 定义挂起取消 Timer Start 等待；实例级等待由实例挂起语义单独管理
            FlowEngine.eventWaitService().cancelByDefinitionId(id);
        }
        return updated;
    }

    /**
     * 按流程编码查询全部历史版本。
     *
     * @param flowCode 流程编码
     * @return 该编码下的流程定义集合
     */
    @Override
    public List<Definition> getByFlowCode(String flowCode) {
        return list(FlowEngine.newDef().setFlowCode(flowCode));
    }

    /**
     * 按流程编码查询当前已发布版本。
     *
     * @param flowCode 流程编码
     * @return 已发布流程定义，不存在时返回 {@code null}
     */
    @Override
    public Definition getPublishByFlowCode(String flowCode) {
        return FlowEngine.defService().getOne(FlowEngine.newDef()
            .setFlowCode(flowCode).setIsPublish(PublishStatus.PUBLISHED.getKey()));
    }

    /**
     * 计算同流程编码的下一版本。
     *
     * <p>优先递增最大的正整数版本；若仅存在非数字版本，则以最新记录的版本追加 {@code _1}。</p>
     *
     * @param definition 待保存流程定义
     * @return 新版本号
     */
    private String getNewVersion(Definition definition) {
        List<String> flowCodeList = List.of(definition.getFlowCode());
        List<Definition> definitions = queryByCodeList(flowCodeList);
        int highestVersion = 0;
        String latestNonPositiveVersion = null;
        long latestTimestamp = Long.MIN_VALUE;

        for (Definition otherDef : definitions) {
            if (definition.getFlowCode().equals(otherDef.getFlowCode())) {
                try {
                    int version = Integer.parseInt(otherDef.getVersion());
                    if (version > highestVersion) {
                        highestVersion = version;
                    }
                } catch (NumberFormatException e) {
                    long timestamp = otherDef.getCreateTime().getTime();
                    if (timestamp > latestTimestamp) {
                        latestTimestamp = timestamp;
                        latestNonPositiveVersion = otherDef.getVersion();
                    }
                }
            }
        }

        String version = "1";
        if (highestVersion > 0) {
            version = String.valueOf(highestVersion + 1);
        } else if (latestNonPositiveVersion != null) {
            version = latestNonPositiveVersion + "_1";
        }

        return version;
    }

    /**
     * 删除指定定义现有的节点和连线，保持节点先于连线的历史调用顺序。
     */
    private void removeGraph(Long definitionId) {
        FlowEngine.nodeService().remove(FlowEngine.newNode().setDefinitionId(definitionId));
        FlowEngine.skipService().remove(FlowEngine.newSkip().setDefinitionId(definitionId));
    }

    /**
     * 保存节点和连线，统一各定义写入入口的图数据持久化顺序。
     */
    private void saveGraph(List<Node> nodes, List<Skip> skips) {
        FlowEngine.nodeService().saveBatch(nodes);
        FlowEngine.skipService().saveBatch(skips);
    }

    /**
     * 设计器未设置协作比例时沿用既有默认值。
     */
    private void normalizeNodeRatios(List<Node> nodes) {
        nodes.forEach(node -> {
            if (StringUtils.isEmpty(node.getNodeRatio())) {
                node.setNodeRatio(StringUtils.ZERO);
            }
        });
    }

    /**
     * 校验并初始化设计器提交的流程结构。
     *
     * <p>校验流程编码与孤立连线后，结构校验核（开始节点数量、节点编码唯一性、跳转合法性、
     * 目标节点存在性）委托 {@link FlowConfigUtil#validateGraph(FlowCombine)}，与导入路径共用。</p>
     *
     * @param flowCombine 待校验的流程组合数据
     */
    private void checkFlowLegal(FlowCombine flowCombine) {
        Definition definition = flowCombine.getDefinition();
        String flowName = definition.getFlowName();
        AssertUtil.isEmpty(definition.getFlowCode(), "【" + flowName + "】流程flowCode为空!");
        // 节点校验
        List<Node> allNodes = flowCombine.getAllNodes();
        List<Skip> allSkips = flowCombine.getAllSkips();
        Map<String, List<Skip>> skipMap = StreamUtils.groupByKey(allSkips, Skip::getNowNodeCode);
        allNodes.forEach(node -> {
            node.setSkipList(skipMap.get(node.getNodeCode()));
            skipMap.remove(node.getNodeCode());
        });
        AssertUtil.isNotEmpty(skipMap, "[" + flowName + "]" + ExceptionCons.FLOW_HAVE_USELESS_SKIP);
        // 结构校验核与导入路径共用，规则保持一致
        FlowConfigUtil.validateGraph(flowCombine);
    }

}

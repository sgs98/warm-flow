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
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.dto.FlowCombine;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Skip;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.orm.dao.FlowDefinitionDao;
import org.dromara.warm.flow.core.orm.service.impl.WarmServiceImpl;
import org.dromara.warm.flow.core.service.DefService;
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
     * <p>新增时生成主键和版本；更新时先清理旧节点、连线再重建。{@code onlyNodeSkip} 为真时保留定义主表字段。</p>
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
     * 删除流程定义及其节点、连线。
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
        });
        FlowEngine.nodeService().deleteNodeByDefIds(ids);
        FlowEngine.skipService().deleteSkipByDefIds(ids);
        return removeByIds(ids);
    }

    /**
     * 发布指定版本，并调整同流程编码下其他已发布版本的状态。
     *
     * <p>已被实例使用的旧版本转为失效，未被使用的旧版本退回未发布，确保同一流程编码只有一个当前发布版本。</p>
     *
     * @param id 待发布定义主键
     * @return 是否更新成功
     */
    @Override
    public boolean publish(Long id) {
        List<Node> nodeList = FlowEngine.nodeService().getByDefId(id);
        AssertUtil.isEmpty(nodeList, ExceptionCons.NOT_DRAW_FLOW_ERROR);
        Definition definition = getById(id);
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
        return updateById(flowDefinition);
    }

    /**
     * 取消发布未被流程实例使用的定义。
     *
     * @param id 流程定义主键
     * @return 是否更新成功
     */
    @Override
    public boolean unPublish(Long id) {
        List<Instance> instances = FlowEngine.insService().getByDefId(id);
        AssertUtil.isNotEmpty(instances, ExceptionCons.EXIST_START_TASK);
        Definition definition = FlowEngine.newDef().setId(id);
        definition.setIsPublish(PublishStatus.UNPUBLISHED.getKey());
        return updateById(definition);
    }

    /**
     * 复制流程定义及其节点、连线，生成同编码下的新版本。
     *
     * @param id 源流程定义主键
     * @return 是否复制成功
     */
    @Override
    public boolean copyDef(Long id) {
        Definition source = getById(id);
        AssertUtil.isNull(source, ExceptionCons.NOT_FOUNT_DEF);
        Definition definition = source.copy();
        definition.setVersion(getNewVersion(definition));

        List<Node> nodeList = FlowEngine.nodeService().getByDefId(id).stream().map(Node::copy).collect(Collectors.toList());
        List<Skip> skipList = FlowEngine.skipService().getByDefId(id).stream().map(Skip::copy).collect(Collectors.toList());
        FlowEngine.dataFillHandler().idFill(definition);

        nodeList.forEach(node -> node.setDefinitionId(definition.getId()));
        skipList.forEach(skip -> skip.setDefinitionId(definition.getId()));
        saveGraph(nodeList, skipList);
        return save(definition);
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
        return updateById(definition);
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
        return updateById(definition);
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
     * <p>校验流程编码、孤立连线、开始节点数量、节点编码唯一性、跳转合法性和目标节点存在性，同时补齐节点定义主键及条件配置。</p>
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
        // 每一个流程的开始节点个数
        Set<String> nodeCodeSet = new HashSet<>();
        // 便利一个流程中的各个节点
        int startNum = 0;
        for (Node node : allNodes) {
            FlowConfigUtil.initNodeAndCondition(node, definition.getId());
            startNum = FlowConfigUtil.checkStartAndSame(node, startNum, flowName, nodeCodeSet);
        }
        AssertUtil.isTrue(startNum == 0, "[" + flowName + "]" + ExceptionCons.LOST_START_NODE);
        // 校验跳转节点的合法性
        FlowConfigUtil.checkSkipNode(allSkips);
        // 校验所有目标节点是否都存在
        FlowConfigUtil.validaIsExistDestNode(allSkips, nodeCodeSet);
    }

}

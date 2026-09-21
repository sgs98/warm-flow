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
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.dto.NodeJson;
import org.dromara.warm.flow.core.dto.PathWayData;
import org.dromara.warm.flow.core.dto.SkipJson;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Skip;
import org.dromara.warm.flow.core.enums.ChartStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.service.ChartService;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.ObjectUtil;
import org.dromara.warm.flow.core.utils.StreamUtils;
import org.dromara.warm.flow.core.utils.StringUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流程图状态元数据服务实现。
 *
 * <p>根据实际流转路径更新设计快照中的节点、连线状态，并将结果序列化后保存到流程实例或返回给调用方。</p>
 *
 * @author warm
 * @since 2024-12-30
 */
public class ChartServiceImpl implements ChartService {

    /**
     * 生成流程启动后的首份流程图元数据。
     *
     * <p>未经过的元素初始化为未完成，已经过的路径标记为完成，当前目标节点标记为待办；结束节点直接标记为完成。</p>
     *
     * @param pathWayData 启动阶段收集的定义ID、经过路径和目标节点
     * @return 带运行状态的流程设计 JSON
     */
    @Override
    public String startMetadata(PathWayData pathWayData) {

        DefJson defJson = FlowEngine.defService().queryDesign(pathWayData.getDefId());
        List<NodeJson> nodeList = defJson.getNodeList();

        Map<String, NodeJson> nodeMap = StreamUtils.toMap(nodeList, NodeJson::getNodeCode
            , node -> node.setStatus(ChartStatus.NOT_DONE.getKey()));
        Map<String, SkipJson> skipMap = nodeList.stream().map(NodeJson::getSkipList).flatMap(List::stream)
            .collect(Collectors.toMap(this::getSkipKey, skip -> skip.setStatus(ChartStatus.NOT_DONE.getKey())));

        pathWayData.getPathWayNodes().forEach(node -> nodeMap.get(node.getNodeCode()).setStatus(ChartStatus.DONE.getKey()));
        pathWayData.getPathWaySkips().forEach(skip -> skipMap.get(getSkipKey(skip)).setStatus(ChartStatus.DONE.getKey()));
        pathWayData.getTargetNodes().forEach(node -> nodeMap.get(node.getNodeCode()).setStatus(
            NodeType.isEnd(node.getNodeType()) ? ChartStatus.DONE.getKey() : ChartStatus.TO_DO.getKey()
        ));

        return FlowEngine.jsonConvert.objToStr(defJson);
    }

    /**
     * 在已有流程图快照上合并一次任务流转结果。
     *
     * <p>通过时推进完成路径，驳回时重置回退路径及其后续分支，避免流程图继续显示已失效的待办状态。</p>
     *
     * @param pathWayData 本次流转的实例ID、跳转类型、经过路径和目标节点
     * @return 更新后的流程图元数据 JSON
     */
    @Override
    public String skipMetadata(PathWayData pathWayData) {
        Instance instance = FlowEngine.insService().getById(pathWayData.getInsId());
        DefJson defJson = FlowEngine.jsonConvert.strToBean(instance.getDefJson(), DefJson.class);

        List<NodeJson> nodeList = defJson.getNodeList();
        List<SkipJson> skipList = StreamUtils.toListAll(defJson.getNodeList(), NodeJson::getSkipList);
        Map<String, NodeJson> nodeMap = StreamUtils.toMap(nodeList, NodeJson::getNodeCode, node -> node);
        Map<String, SkipJson> skipMap = StreamUtils.toMap(skipList, this::getSkipKey, skip -> skip);

        pathWayData.getPathWayNodes().forEach(node -> {
            NodeJson nodeJson = nodeMap.get(node.getNodeCode());
            if (SkipType.isPass(pathWayData.getSkipType())) {
                nodeJson.setStatus(ChartStatus.DONE.getKey());
            } else if (SkipType.isReject(pathWayData.getSkipType())) {
                nodeJson.setStatus(ChartStatus.NOT_DONE.getKey());
            }
        });
        pathWayData.getPathWaySkips().forEach(skip -> {
            SkipJson skipJson = skipMap.get(getSkipKey(skip));
            if (SkipType.isPass(pathWayData.getSkipType())) {
                skipJson.setStatus(ChartStatus.DONE.getKey());
            } else if (SkipType.isReject(pathWayData.getSkipType())) {
                skipJson.setStatus(ChartStatus.NOT_DONE.getKey());
            }
        });
        pathWayData.getTargetNodes().forEach(node -> {
            NodeJson nodeJson = nodeMap.get(node.getNodeCode());
            if (NodeType.isEnd(node.getNodeType())) {
                nodeJson.setStatus(ChartStatus.DONE.getKey());
            } else {
                nodeJson.setStatus(ChartStatus.TO_DO.getKey());
            }
        });

        if (SkipType.isReject(pathWayData.getSkipType())) {
            Map<String, List<SkipJson>> skipNextMap = StreamUtils.groupByKeyFilter(skip ->
                !SkipType.isReject(skip.getSkipType()), skipList, SkipJson::getNowNodeCode);
            pathWayData.getTargetNodes().forEach(node -> rejectReset(node.getNodeCode(), skipNextMap, nodeMap));
        }

        pathWayData.getTargetNodes().forEach(node -> {
            if (NodeType.isEnd(node.getNodeType())) {
                nodeList.forEach(nodeJson -> {
                    if (ChartStatus.isToDo(nodeJson.getStatus())) {
                        nodeJson.setStatus(ChartStatus.NOT_DONE.getKey());
                    }
                });
            }
        });


        return FlowEngine.jsonConvert.objToStr(defJson);
    }

    /**
     * 获取指定流程图配色模型对应的三种状态颜色。
     *
     * @param modelValue 配色模型标识
     * @return 按已完成、待办、未完成顺序排列的 RGB 字符串
     */
    @Override
    public List<String> getChartRgb(String modelValue) {
        List<String> chartStatusColor = new ArrayList<>();
        Color done = ChartStatus.getDone(modelValue);
        chartStatusColor.add(done.getRed() + "," + done.getGreen() + "," + done.getBlue());
        Color toDo = ChartStatus.getToDo(modelValue);
        chartStatusColor.add(toDo.getRed() + "," + toDo.getGreen() + "," + toDo.getBlue());
        Color notDone = ChartStatus.getNotDone(modelValue);
        chartStatusColor.add(notDone.getRed() + "," + notDone.getGreen() + "," + notDone.getBlue());
        return chartStatusColor;
    }

    /**
     * 使用连线的起点、类型、条件和终点构造设计快照中的唯一匹配键。
     *
     * @param skip 设计快照中的连线
     * @return 连线唯一匹配键
     */
    private String getSkipKey(SkipJson skip) {
        return StringUtils.join(new String[]{
            skip.getNowNodeCode(),
            skip.getSkipType(),
            skip.getSkipCondition(),
            skip.getNextNodeCode()}, ":");
    }

    /**
     * 使用运行期连线的起点、类型、条件和终点构造唯一匹配键。
     *
     * @param skip 运行期连线
     * @return 连线唯一匹配键
     */
    private String getSkipKey(Skip skip) {
        return StringUtils.join(new String[]{
            skip.getNowNodeCode(),
            skip.getSkipType(),
            skip.getSkipCondition(),
            skip.getNextNodeCode()}, ":");
    }

    /**
     * 从驳回目标开始递归重置后续非驳回路径，清除已不再有效的完成或待办状态。
     *
     * @param nodeCode    当前递归节点编码
     * @param skipNextMap 按起点分组的后续连线
     * @param nodeMap     按节点编码索引的设计节点
     */
    private void rejectReset(String nodeCode, Map<String, List<SkipJson>> skipNextMap, Map<String, NodeJson> nodeMap) {
        List<SkipJson> oneNextSkips = skipNextMap.get(nodeCode);
        if (CollUtil.isNotEmpty(oneNextSkips)) {
            oneNextSkips.forEach(oneNextSkip -> {
                if (ObjectUtil.isNotNull(oneNextSkip) && !ChartStatus.isNotDone(oneNextSkip.getStatus())) {
                    oneNextSkip.setStatus(ChartStatus.NOT_DONE.getKey());
                    NodeJson nodeJson = nodeMap.get(oneNextSkip.getNextNodeCode());
                    if (ObjectUtil.isNotNull(nodeJson) && !ChartStatus.isNotDone(nodeJson.getStatus())) {
                        nodeJson.setStatus(ChartStatus.NOT_DONE.getKey());
                        rejectReset(nodeJson.getNodeCode(), skipNextMap, nodeMap);
                    }
                }
            });
        }
    }
}

package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.*;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.StreamUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 流程节点路径解析器。
 *
 * <p>只计算本次操作经过和到达的节点，不执行任务、历史或实例写入。</p>
 *
 * @author may
 */
final class FlowPathResolver {

    /**
     * 解析当前节点到目标节点的完整路径，并处理并行、包容网关的汇聚等待。
     *
     * @param task        当前待办任务
     * @param nowNode     当前流程节点
     * @param instance    流程实例
     * @param flowParams  流程操作参数
     * @param flowCombine 流程定义组合数据
     * @return 本次流转的途经节点、跳转线和目标节点
     */
    PathWayData resolve(Task task, Node nowNode, Instance instance, FlowParams flowParams, FlowCombine flowCombine) {
        PathWayData pathWayData = new PathWayData().setInsId(task.getInstanceId())
            .setSkipType(flowParams.getSkipType());
        Node nextNode = FlowEngine.nodeService().getNextNode(nowNode, flowParams.getNodeCode()
            , flowParams.getSkipType(), pathWayData, flowCombine);
        List<Node> nextNodes = FlowEngine.nodeService().getNextByCheckGateway(flowParams.getVariable()
            , nextNode, pathWayData, flowCombine);
        retainJoinPath(pathWayData, instance, nextNodes);
        pathWayData.getTargetNodes().addAll(nextNodes);
        return pathWayData;
    }

    /**
     * 汇聚网关仍有其他活动前置任务时，截断网关后的路径并取消新任务生成。
     *
     * @param pathWayData 已解析的路径数据
     * @param instance    流程实例
     * @param nextNodes   候选目标节点，可根据汇聚状态清空
     */
    private void retainJoinPath(PathWayData pathWayData, Instance instance, List<Node> nextNodes) {
        if (SkipType.isReject(pathWayData.getSkipType())) {
            return;
        }

        List<Node> gateways = Optional.of(pathWayData)
            .map(PathWayData::getPathWayNodes)
            .orElse(Collections.emptyList())
            .stream()
            .filter(node -> NodeType.isGateWayParallel(node.getNodeType())
                || NodeType.isGateWayInclusive(node.getNodeType()))
            .collect(Collectors.toList());
        if (CollUtil.isEmpty(gateways)) {
            return;
        }

        List<Node> previousNodes = FlowEngine.nodeService().previousNodeList(instance.getDefinitionId()
            , gateways.get(gateways.size() - 1).getNodeCode());
        List<String> previousNodeCodes = StreamUtils.toList(previousNodes, Node::getNodeCode);
        List<Task> activePreviousTasks = FlowEngine.taskService().getByInsIdAndNodeCodes(instance.getId()
            , previousNodeCodes);
        if (activePreviousTasks.size() <= 1) {
            return;
        }

        nextNodes.clear();
        AtomicBoolean reachedGateway = new AtomicBoolean(false);
        pathWayData.getPathWayNodes().removeIf(nodeJson -> {
            if (nodeJson.getNodeCode().equals(gateways.get(0).getNodeCode())) {
                reachedGateway.set(true);
                return false;
            }
            return reachedGateway.get();
        });
        reachedGateway.set(false);
        pathWayData.getPathWaySkips().removeIf(skipJson -> {
            if (skipJson.getNowNodeCode().equals(gateways.get(0).getNodeCode())) {
                reachedGateway.set(true);
            }
            return reachedGateway.get();
        });
    }
}

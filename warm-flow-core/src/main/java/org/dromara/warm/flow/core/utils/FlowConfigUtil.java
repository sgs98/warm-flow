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

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.dto.FlowCombine;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Skip;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.service.task.ServiceTaskConfig;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 流程配置帮助类
 *
 * @author zhoukai
 */
public class FlowConfigUtil {
    /**
     * 无参构造
     */
    private FlowConfigUtil() {

    }

    public static FlowCombine structureFlow(Definition definition) {
        // 获取流程
        FlowCombine combine = new FlowCombine();
        // 流程定义
        combine.setDefinition(definition);

        String flowName = definition.getFlowName();
        AssertUtil.isEmpty(definition.getFlowCode(), "【" + flowName + "】流程flowCode为空!");
        // 发布
        definition.setIsPublish(0);
        definition.setUpdateTime(new Date());
        FlowEngine.dataFillHandler().idFill(definition);

        // 节点与连线汇入定义图后统一校验
        definition.getNodeList().forEach(node -> {
            combine.getAllNodes().add(node);
            combine.getAllSkips().addAll(node.getSkipList());
        });
        validateGraph(combine);
        return combine;
    }

    /**
     * 校验定义图的结构合法性：补齐节点与连线配置、保证唯一开始节点和节点编码唯一，
     * 并从目标节点派生连线目标节点类型后，校验中间节点多出口限制与目标节点存在性。
     * 设计器保存与定义导入共用此校验核，保证两条路径规则一致。
     *
     * @param combine 含定义、节点和连线的流程组合数据，节点与连线须已齐备
     */
    public static void validateGraph(FlowCombine combine) {
        Definition definition = combine.getDefinition();
        String flowName = definition.getFlowName();
        List<Node> allNodes = combine.getAllNodes();
        List<Skip> allSkips = combine.getAllSkips();
        // 每一个流程的开始节点个数
        int startNum = 0;
        Set<String> nodeCodeSet = new HashSet<>();
        // 遍历一个流程中的各个节点
        for (Node node : allNodes) {
            initNodeAndCondition(node, definition.getId());
            startNum = checkStartAndSame(node, startNum, flowName, nodeCodeSet);
        }
        AssertUtil.isTrue(startNum == 0, "[" + flowName + "]" + ExceptionCons.LOST_START_NODE);
        // 从目标节点派生连线目标节点类型，不信任调用方回传值
        Map<String, Integer> nodeTypeMap = StreamUtils.toMap(allNodes, Node::getNodeCode, Node::getNodeType);
        allSkips.forEach(skip -> skip.setNextNodeType(nodeTypeMap.get(skip.getNextNodeCode())));
        // 校验跳转节点的合法性
        checkSkipNode(allSkips);
        // 校验所有目标节点是否都存在
        validaIsExistDestNode(allSkips, nodeCodeSet);
        // 校验服务任务节点配置与出口
        Map<String, List<Skip>> skipGroup = StreamUtils.groupByKey(allSkips, Skip::getNowNodeCode);
        for (Node node : allNodes) {
            checkServiceNode(node, skipGroup.get(node.getNodeCode()));
        }
    }

    /**
     * 校验服务任务节点配置与出口。
     *
     * @param node     节点
     * @param outgoing 该节点的出口连线
     */
    private static void checkServiceNode(Node node, List<Skip> outgoing) {
        if (!NodeType.isService(node.getNodeType())) {
            return;
        }
        // 保存期只做格式校验，不加载处理器类
        ServiceTaskConfig.parse(node.getNodeConfig());
        if (CollUtil.isEmpty(outgoing)) {
            return;
        }
        for (Skip skip : outgoing) {
            AssertUtil.isTrue(!SkipType.isPass(skip.getSkipType()), ExceptionCons.SERVICE_TASK_ONLY_PASS);
        }
    }

    public static int checkStartAndSame(Node node, int startNum, String flowName, Set<String> nodeCodeSet) {
        if (NodeType.isStart(node.getNodeType())) {
            startNum++;
            AssertUtil.isTrue(startNum > 1, "[" + flowName + "]" + ExceptionCons.MUL_START_NODE);
        }
        // 保证不存在重复的nodeCode
        AssertUtil.contains(nodeCodeSet, node.getNodeCode(),
            "【" + flowName + "】" + ExceptionCons.SAME_NODE_CODE);
        nodeCodeSet.add(node.getNodeCode());
        return startNum;
    }

    /**
     * 校验跳转节点的合法性
     *
     * @param allSkips
     */
    public static void checkSkipNode(List<Skip> allSkips) {
        Map<String, List<Skip>> allSkipMap = StreamUtils.groupByKey(allSkips, Skip::getNowNodeCode);
        // 不可同时通过或者退回到多个中间节点，必须先流转到网关节点
        allSkipMap.forEach((key, values) -> {
            AtomicInteger passNum = new AtomicInteger();
            AtomicInteger rejectNum = new AtomicInteger();
            for (Skip value : values) {
                if (NodeType.isBetween(value.getNowNodeType()) && NodeType.isBetween(value.getNextNodeType())) {
                    if (SkipType.isPass(value.getSkipType())) {
                        passNum.getAndIncrement();
                    } else {
                        rejectNum.getAndIncrement();
                    }
                }
            }
            AssertUtil.isTrue(passNum.get() > 1 || rejectNum.get() > 1, ExceptionCons.MUL_SKIP_BETWEEN);
        });
    }

    /**
     * 校验所有的目标节点是否存在
     *
     * @param allSkips
     * @param nodeCodeSet
     */
    public static void validaIsExistDestNode(List<Skip> allSkips, Set<String> nodeCodeSet) {
        for (Skip allSkip : allSkips) {
            String nextNodeCode = allSkip.getNextNodeCode();
            AssertUtil.isTrue(!nodeCodeSet.contains(nextNodeCode), "【" + nextNodeCode + "】" + ExceptionCons.NULL_NODE_CODE);
        }
    }


    /**
     * 读取工作节点和跳转条件
     *
     * @param node         node
     * @param definitionId definitionId
     */
    public static void initNodeAndCondition(Node node, Long definitionId) {
        String nodeName = node.getNodeName();
        String nodeCode = node.getNodeCode();
        List<Skip> skipList = node.getSkipList();
        if (!NodeType.isEnd(node.getNodeType())) {
            AssertUtil.isEmpty(skipList, ExceptionCons.MUST_SKIP);
        }
        AssertUtil.isEmpty(nodeCode, "[" + nodeName + "]" + ExceptionCons.LOST_NODE_CODE);

        node.setDefinitionId(definitionId);

        // 中间节点的集合， 跳转类型和目标节点不能重复
        Set<String> betweenSet = new HashSet<>();
        // 网关的集合 跳转条件和下目标节点不能重复
        Set<String> gateWaySet = new HashSet<>();
        int skipNum = 0;
        // 遍历节点下的跳转条件
        if (CollUtil.isEmpty(skipList)) {
            return;
        }
        for (Skip skip : skipList) {
            if (NodeType.isStart(node.getNodeType())) {
                skipNum++;
                AssertUtil.isTrue(skipNum > 1, "[" + node.getNodeName() + "]" + ExceptionCons.MUL_START_SKIP);
            }
            AssertUtil.isEmpty(skip.getNextNodeCode(), "【" + nodeName + "】" + ExceptionCons.LOST_DEST_NODE);
            // 流程id
            skip.setDefinitionId(definitionId);
            skip.setNowNodeType(node.getNodeType());
            if (NodeType.isGateWaySerial(node.getNodeType())) {
                String target = skip.getSkipCondition() + ":" + skip.getNextNodeCode();
                AssertUtil.contains(gateWaySet, target, "[" + nodeName + "]" + ExceptionCons.SAME_CONDITION_NODE);
                gateWaySet.add(target);
            } else if (NodeType.isGateWayParallel(node.getNodeType())) {
                String target = skip.getNextNodeCode();
                AssertUtil.contains(gateWaySet, target, "[" + nodeName + "]" + ExceptionCons.SAME_DEST_NODE);
                gateWaySet.add(target);
            } else {
                String value = skip.getSkipType() + ":" + skip.getNextNodeCode();
                AssertUtil.contains(betweenSet, value, "[" + nodeName + "]" + ExceptionCons.SAME_CONDITION_VALUE);
                betweenSet.add(value);
            }
        }
    }

}

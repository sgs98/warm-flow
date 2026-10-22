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
import org.dromara.warm.flow.core.enums.EventType;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.service.event.EventConfig;
import org.dromara.warm.flow.core.service.task.ServiceTaskConfig;
import org.dromara.warm.flow.core.timer.TimerContext;
import org.dromara.warm.flow.core.timer.TimerScheduleStrategies;

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
     * 并从目标节点派生连线目标节点类型后，校验中间节点多出口限制与目标节点存在性，
     * 以及服务任务与事件节点的结构与字段。
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
        // 校验服务任务节点配置与出口，以及事件节点结构与字段
        Map<String, List<Skip>> skipGroup = StreamUtils.groupByKey(allSkips, Skip::getNowNodeCode);
        Map<String, List<Skip>> incomingGroup = StreamUtils.groupByKey(allSkips, Skip::getNextNodeCode);
        // 节点编码已在上方校验唯一，可直接建立索引供边界事件附着校验使用
        Map<String, Node> nodeMap = new HashMap<>();
        for (Node item : allNodes) {
            nodeMap.put(item.getNodeCode(), item);
        }
        for (Node node : allNodes) {
            checkServiceNode(node, skipGroup.get(node.getNodeCode()));
            checkEventNode(node, nodeMap, skipGroup.get(node.getNodeCode()), incomingGroup.get(node.getNodeCode()));
        }
        // 并行 / 包容网关不允许产生多个无待办的事件等待分支
        checkGatewayEventBranches(allNodes, nodeMap, skipGroup);
        // 同一定义不能混用消息启动与定时启动
        checkStartEventTypeConsistency(allNodes);
    }

    /**
     * 校验事件节点结构与事件字段。
     *
     * <p>固定 {@code START} / {@code END} 不承载事件属性，且开始节点不能有入线、结束节点不能有出线；
     * 事件节点必须使用中间节点（{@code BETWEEN}）承载，并按事件类型校验拓扑与必填字段；无事件的普通
     * 节点直接跳过。边界事件的附着节点必须存在且为未配置事件的中间任务节点，防止导入路径绕过前端
     * 下拉限制。草稿与导入同样执行本校验，未开放运行时的事件类型由发布门禁
     * {@link #validateEventCapability(FlowCombine)} 另行拦截。</p>
     *
     * @param node     节点
     * @param nodeMap  当前定义的节点索引（编码唯一，键为节点编码）
     * @param outgoing 该节点的出口连线
     * @param incoming 该节点的入口连线
     */
    private static void checkEventNode(Node node, Map<String, Node> nodeMap, List<Skip> outgoing
        , List<Skip> incoming) {
        String eventType = node.getEventType();
        boolean hasEvent = StringUtils.isNotEmpty(eventType);
        // 固定开始 / 结束节点不允许承载事件属性，且开始节点不能有入线、结束节点不能有出线
        if (NodeType.isStart(node.getNodeType()) || NodeType.isEnd(node.getNodeType())) {
            AssertUtil.isTrue(hasEvent, "[" + node.getNodeCode() + "]" + ExceptionCons.FIXED_NODE_NO_EVENT);
            boolean startHasIncoming = NodeType.isStart(node.getNodeType()) && CollUtil.isNotEmpty(incoming);
            boolean endHasOutgoing = NodeType.isEnd(node.getNodeType()) && CollUtil.isNotEmpty(outgoing);
            AssertUtil.isTrue(startHasIncoming || endHasOutgoing,
                "[" + node.getNodeCode() + "]" + ExceptionCons.FIXED_NODE_LINE_ERROR);
            return;
        }
        if (!hasEvent) {
            return;
        }
        // 事件节点统一使用中间节点承载，网关 / 服务任务节点不允许事件属性
        AssertUtil.isTrue(!NodeType.isBetween(node.getNodeType()),
            "[" + node.getNodeCode() + "]" + ExceptionCons.EVENT_NODE_TYPE_INVALID);
        EventType type = EventType.getByValue(eventType);
        AssertUtil.isNull(type, "[" + node.getNodeCode() + "]" + ExceptionCons.ILLEGAL_EVENT_TYPE);

        // 启动事件：紧邻固定开始节点，只有来自开始节点的一条入线和一条普通出口
        if (EventType.isStart(eventType)) {
            boolean singleFromStart = CollUtil.isNotEmpty(incoming) && incoming.size() == 1
                && NodeType.isStart(incoming.get(0).getNowNodeType());
            boolean singlePass = CollUtil.isNotEmpty(outgoing) && outgoing.size() == 1
                && SkipType.isPass(outgoing.get(0).getSkipType());
            AssertUtil.isTrue(!(singleFromStart && singlePass),
                "[" + node.getNodeCode() + "]" + ExceptionCons.START_EVENT_TOPOLOGY);
        }
        // 结束事件：必须有入线，且唯一出口直接指向固定结束节点
        if (EventType.isEnd(eventType)) {
            boolean singleToEnd = CollUtil.isNotEmpty(outgoing) && outgoing.size() == 1
                && NodeType.isEnd(outgoing.get(0).getNextNodeType());
            AssertUtil.isTrue(CollUtil.isEmpty(incoming) || !singleToEnd,
                "[" + node.getNodeCode() + "]" + ExceptionCons.END_EVENT_TOPOLOGY);
        }
        // 捕获事件需出线，抛出事件需入线
        if (EventType.isCatch(eventType)) {
            AssertUtil.isEmpty(outgoing, "[" + node.getNodeCode() + "]" + ExceptionCons.CATCH_EVENT_NO_OUTGOING);
        }
        if (EventType.isThrow(eventType)) {
            AssertUtil.isEmpty(incoming, "[" + node.getNodeCode() + "]" + ExceptionCons.THROW_EVENT_NO_INCOMING);
        }
        // 边界事件：必须配置附着节点，附着节点必须存在且为未配置事件的中间任务节点，第一阶段固定为中断型
        if (EventType.isBoundary(eventType)) {
            AssertUtil.isEmpty(node.getAttachedToNodeCode(),
                "[" + node.getNodeCode() + "]" + ExceptionCons.BOUNDARY_EVENT_NO_ATTACHED);
            Node attached = nodeMap.get(node.getAttachedToNodeCode());
            AssertUtil.isNull(attached,
                "[" + node.getNodeCode() + "]" + ExceptionCons.BOUNDARY_EVENT_ATTACHED_NOT_EXIST);
            AssertUtil.isTrue(!NodeType.isBetween(attached.getNodeType())
                    || StringUtils.isNotEmpty(attached.getEventType()),
                "[" + node.getNodeCode() + "]" + ExceptionCons.BOUNDARY_EVENT_ATTACHED_INVALID);
            AssertUtil.isTrue(!Boolean.TRUE.equals(node.getInterrupting()),
                "[" + node.getNodeCode() + "]" + ExceptionCons.BOUNDARY_EVENT_NOT_INTERRUPTING);
        }
        // 消息事件必须有消息名称，定时事件必须有合法定时配置
        if (EventType.isMessage(eventType)) {
            AssertUtil.isEmpty(node.getEventName(), "[" + node.getNodeCode() + "]" + ExceptionCons.MESSAGE_EVENT_NO_NAME);
        }
        if (EventType.isTimer(eventType)) {
            EventConfig config = EventConfig.parse(node.getNodeConfig());
            AssertUtil.isTrue(!config.hasTimer(),
                "[" + node.getNodeCode() + "]" + ExceptionCons.TIMER_EVENT_NO_CONFIG);
            // 试算一次下次触发时间：timerValue / timezone 格式错误在保存与发布期即暴露，
            // 而不是等到定时启动建等待或运行到达捕获 / 边界节点时才失败
            try {
                TimerScheduleStrategies.nextTriggerAt(config.getTimerType(), new TimerContext()
                    .setPreviousTriggerAt(null)
                    .setCurrentTime(new Date())
                    .setTriggerInterval(config.getTimerValue())
                    .setTimezone(config.getTimezone())
                    .setRepeatCount(0));
            } catch (FlowException e) {
                throw new FlowException("[" + node.getNodeCode() + "]" + e.getMessage());
            }
        }
    }

    /**
     * 并行 / 包容网关不允许同时激活多个无待办的事件等待分支。
     *
     * <p>等待分支不产生待办，多分支同时等待会与实例单节点位置字段和网关汇聚计数产生歧义
     * （事件设计 §8.0 约定 5），保存与导入路径统一拒绝；串行网关同一时刻只激活一条分支，
     * 单事件等待分支与普通任务分支并存不受限。</p>
     *
     * @param allNodes  定义内全部节点
     * @param nodeMap   当前定义的节点索引（编码唯一，键为节点编码）
     * @param skipGroup 按当前节点编码分组的连线
     */
    private static void checkGatewayEventBranches(List<Node> allNodes, Map<String, Node> nodeMap
        , Map<String, List<Skip>> skipGroup) {
        for (Node node : allNodes) {
            if (!NodeType.isGateWayParallel(node.getNodeType()) && !NodeType.isGateWayInclusive(node.getNodeType())) {
                continue;
            }
            List<Skip> outgoing = skipGroup.get(node.getNodeCode());
            if (CollUtil.isEmpty(outgoing)) {
                continue;
            }
            long waitBranches = outgoing.stream()
                .filter(skip -> SkipType.isPass(skip.getSkipType()))
                .map(skip -> nodeMap.get(skip.getNextNodeCode()))
                .filter(target -> target != null && StringUtils.isNotEmpty(target.getEventType())
                    && EventType.isCatch(target.getEventType()))
                .count();
            AssertUtil.isTrue(waitBranches > 1,
                "[" + node.getNodeCode() + "]" + ExceptionCons.GATEWAY_MULTI_EVENT_WAIT_BRANCHES);
        }
    }

    /**
     * 同一定义不能同时存在消息启动与定时启动事件。
     *
     * @param allNodes 定义内全部节点
     */
    private static void checkStartEventTypeConsistency(List<Node> allNodes) {
        boolean hasMessageStart = false;
        boolean hasTimerStart = false;
        for (Node node : allNodes) {
            String eventType = node.getEventType();
            if (StringUtils.isEmpty(eventType)) {
                continue;
            }
            hasMessageStart = hasMessageStart || EventType.MESSAGE_START.getValue().equals(eventType);
            hasTimerStart = hasTimerStart || EventType.TIMER_START.getValue().equals(eventType);
        }
        AssertUtil.isTrue(hasMessageStart && hasTimerStart, ExceptionCons.EVENT_MIX_START_TYPE);
    }

    /**
     * 发布能力门禁：只允许发布运行时能力已开放的事件类型。
     *
     * <p>草稿可以保留未开放的事件字段，本校验只在发布入口调用，不参与保存与导入。</p>
     *
     * @param combine 含定义、节点和连线的流程组合数据
     */
    public static void validateEventCapability(FlowCombine combine) {
        for (Node node : combine.getAllNodes()) {
            EventType type = EventType.getByValue(node.getEventType());
            if (type == null) {
                continue;
            }
            AssertUtil.isTrue(!type.isRuntimeEnabled(),
                "[" + node.getNodeCode() + "]" + ExceptionCons.EVENT_NOT_PUBLISHABLE);
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

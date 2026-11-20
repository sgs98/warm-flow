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
import org.dromara.warm.flow.core.dto.GroupSignConfig;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Skip;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.EventType;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.service.event.EventConfig;
import org.dromara.warm.flow.core.service.task.RejectConfig;
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
        Set<String> branchNodes = null;
        for (Node source : allNodes) {
            try {
                if (!RejectConfig.parse(source.getNodeConfig()).isReturnToRejector()) {
                    continue;
                }
                // 静态目标（anyNodeSkip 或唯一退回线）存在时提前做路径预校验，把可静态发现的问题
                // 挡在保存期；两者都没有时允许保存——目标可由办理人驳回时指定，路径合法性
                // 由运行时 rejectReturnPreflight 的 validateRejectReturnPath 兜底（同事务回滚）。
                String target = source.getAnyNodeSkip();
                if (StringUtils.isEmpty(target)) {
                    List<Skip> rejects = allSkips.stream().filter(s -> source.getNodeCode().equals(s.getNowNodeCode())
                        && SkipType.isReject(s.getSkipType())).collect(java.util.stream.Collectors.toList());
                    if (rejects.size() == 1) {
                        target = rejects.get(0).getNextNodeCode();
                    }
                }
                if (StringUtils.isNotEmpty(target)) {
                    if (branchNodes == null) {
                        branchNodes = parallelBranchNodes(combine);
                    }
                    validateRejectReturnPath(combine, source.getNodeCode(), target, branchNodes);
                }
            } catch (FlowException e) {
                throw new FlowException("[" + source.getNodeCode() + "]" + e.getMessage());
            }
        }
        for (Node node : allNodes) {
            checkServiceNode(node, skipGroup.get(node.getNodeCode()));
            checkEventNode(node, nodeMap, skipGroup.get(node.getNodeCode()), incomingGroup.get(node.getNodeCode()));
            checkCooperateType(node);
        }
        // 并行 / 包容网关不允许产生多个无待办的事件等待分支
        checkGatewayEventBranches(allNodes, nodeMap, skipGroup);
        // 同一定义不能混用消息启动与定时启动
        checkStartEventTypeConsistency(allNodes);
    }

    /**
     * 校验实际驳回目标到发起节点的唯一人工 PASS 路径，运行期显式目标也复用此门禁。
     *
     * @param combine    当前运行定义图
     * @param sourceCode 驳回发起节点
     * @param targetCode 本次实际驳回目标
     */
    public static void validateRejectReturnPath(FlowCombine combine, String sourceCode, String targetCode) {
        validateRejectReturnPath(combine, sourceCode, targetCode, parallelBranchNodes(combine));
    }

    private static void validateRejectReturnPath(FlowCombine combine, String sourceCode, String targetCode,
        Set<String> branchNodes) {
        String prefix = "[" + sourceCode + "]驳回直返";
        Map<String, Node> nodes = StreamUtils.toMap(combine.getAllNodes(), Node::getNodeCode, node -> node);
        AssertUtil.isTrue(Objects.equals(sourceCode, targetCode), prefix + "必须返回之前的人工节点");
        Node sourceNode = nodes.get(sourceCode);
        AssertUtil.isTrue(sourceNode == null || !NodeType.isBetween(sourceNode.getNodeType())
            || StringUtils.isNotEmpty(sourceNode.getEventType()), prefix + "驳回发起节点必须是普通人工节点");
        if (branchNodes.contains(sourceCode)) {
            // 并行 / 包容区间（或驳回节点本身位于并行/包容分支内）按「整体退回重走」处理：
            // 配置合法但不启用直返，A 提交后重新分叉或沿分支重走，不会直达驳回节点。
            Node target = nodes.get(targetCode);
            Node source = nodes.get(sourceCode);
            AssertUtil.isTrue(target == null || source == null, prefix + "目标或来源节点不存在");
            AssertUtil.isTrue(!NodeType.isBetween(target.getNodeType())
                    || StringUtils.isNotEmpty(target.getEventType()),
                prefix + "驳回目标必须是普通人工节点");
            AssertUtil.isTrue(!NodeType.isBetween(source.getNodeType())
                    || StringUtils.isNotEmpty(source.getEventType()),
                prefix + "驳回发起节点必须是普通人工节点");
            return;
        }
        Map<String, List<Skip>> outgoing = combine.getAllSkips().stream().filter(skip -> SkipType.isPass(skip.getSkipType()))
            .collect(java.util.stream.Collectors.groupingBy(Skip::getNowNodeCode));
        Map<String, List<Skip>> incoming = combine.getAllSkips().stream().filter(skip -> SkipType.isPass(skip.getSkipType()))
            .collect(java.util.stream.Collectors.groupingBy(Skip::getNextNodeCode));
        Set<String> reachingSource = nodesReachingSource(incoming, sourceCode);
        if (crossesParallelGateway(combine, targetCode, sourceCode)) {
            validateGatewayReturnInterval(combine, nodes, outgoing, reachingSource, sourceCode, targetCode, prefix);
            return;
        }
        Set<String> interval = new HashSet<>();
        // 目标与发起节点都必须是普通人工节点（网关只允许作为区间内的中转，不能被驳回或驳回）
        Node targetNode = nodes.get(targetCode);
        AssertUtil.isTrue(targetNode == null || !NodeType.isBetween(targetNode.getNodeType())
                || StringUtils.isNotEmpty(targetNode.getEventType()),
            prefix + "驳回目标必须是普通人工节点");
        String code = targetCode;
        while (true) {
            Node node = nodes.get(code);
            AssertUtil.isTrue(node == null || !interval.add(code), prefix + "目标不存在或返回区间存在循环");
            // 会签 / 票签 / 顺签 / 组签节点允许出现在区间内并被直返跳过；
            // 排他网关为单令牌选路、直返不执行区间内节点，也允许（要求唯一一条通往驳回节点的分支）；
            // 服务任务与事件节点仍然禁止（跳过会丢失副作用或等待语义）。
            AssertUtil.isTrue(!NodeType.isGateWaySerial(node.getNodeType())
                    && (NodeType.isService(node.getNodeType()) || NodeType.isGateWay(node.getNodeType())
                    || StringUtils.isNotEmpty(node.getEventType())),
                prefix + "返回区间不支持并行/包容网关、服务任务或事件节点");
            if (sourceCode.equals(code)) {
                break;
            }
            String current = code;
            List<Skip> passes = outgoing.getOrDefault(current, List.of());
            if (NodeType.isGateWaySerial(node.getNodeType())) {
                // 排他网关按条件选路，但直返不执行网关：只要求恰好一条分支能到达驳回节点
                long reaching = passes.stream()
                    .filter(s -> reachingSource.contains(s.getNextNodeCode()))
                    .count();
                AssertUtil.isTrue(reaching != 1, prefix + "排他网关存在多条或没有通往驳回节点的分支");
                code = passes.stream()
                    .filter(s -> reachingSource.contains(s.getNextNodeCode()))
                    .findFirst().orElseThrow().getNextNodeCode();
            } else {
                AssertUtil.isTrue(passes.size() != 1 || StringUtils.isNotEmpty(passes.get(0).getSkipCondition()),
                    prefix + "返回区间必须具有唯一无条件通过路径");
                code = passes.get(0).getNextNodeCode();
            }
            String next = code;
            int incomingCount = incoming.getOrDefault(next, List.of()).size();
            AssertUtil.isTrue(incomingCount != 1, prefix + "返回区间不支持分支汇聚");
        }
        for (Node node : combine.getAllNodes()) {
            AssertUtil.isTrue(StringUtils.isNotEmpty(node.getAttachedToNodeCode())
                && interval.contains(node.getAttachedToNodeCode()), prefix + "返回区间不支持附着边界事件");
        }
    }

    private static void validateGatewayReturnInterval(FlowCombine combine, Map<String, Node> nodes,
        Map<String, List<Skip>> outgoing, Set<String> reachingSource, String sourceCode, String targetCode, String prefix) {
        Node source = nodes.get(sourceCode);
        Node target = nodes.get(targetCode);
        AssertUtil.isTrue(source == null || target == null || !NodeType.isBetween(source.getNodeType())
            || !NodeType.isBetween(target.getNodeType()) || StringUtils.isNotEmpty(source.getEventType())
            || StringUtils.isNotEmpty(target.getEventType()), prefix + "目标和来源必须是普通人工节点");
        Set<String> interval = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>(List.of(targetCode));
        boolean reachedSource = false;
        while (!pending.isEmpty()) {
            String code = pending.poll();
            if (!interval.add(code)) continue;
            Node node = nodes.get(code);
            AssertUtil.isTrue(node == null || NodeType.isService(node.getNodeType())
                || StringUtils.isNotEmpty(node.getEventType()), prefix + "返回区间不支持服务任务或事件节点");
            if (sourceCode.equals(code)) {
                reachedSource = true;
                continue;
            }
            List<Skip> passes = outgoing.getOrDefault(code, List.of());
            List<Skip> reaching = passes.stream().filter(skip -> reachingSource.contains(skip.getNextNodeCode()))
                .collect(java.util.stream.Collectors.toList());
            if (NodeType.isGateWaySerial(node.getNodeType())) {
                AssertUtil.isTrue(reaching.size() != 1, prefix + "排他网关存在多条或没有通往驳回节点的分支");
            } else if (NodeType.isGateWayParallel(node.getNodeType()) || NodeType.isGateWayInclusive(node.getNodeType())) {
                AssertUtil.isTrue(reaching.isEmpty() || reaching.size() != passes.size(), prefix + "并行/包容返回分支必须完整汇聚");
            } else {
                AssertUtil.isTrue(!NodeType.isBetween(node.getNodeType()) || passes.size() != 1 || reaching.size() != 1
                    || StringUtils.isNotEmpty(passes.get(0).getSkipCondition()), prefix + "人工返回路径必须唯一且无条件");
            }
            reaching.forEach(skip -> pending.add(skip.getNextNodeCode()));
        }
        AssertUtil.isFalse(reachedSource, prefix + "目标必须是之前的人工节点");
        for (Node node : combine.getAllNodes()) {
            AssertUtil.isTrue(StringUtils.isNotEmpty(node.getAttachedToNodeCode()) && interval.contains(node.getAttachedToNodeCode()),
                prefix + "返回区间不支持附着边界事件");
        }
    }

    private static Set<String> nodesReachingSource(Map<String, List<Skip>> incoming, String sourceCode) {
        Set<String> reachable = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>(List.of(sourceCode));
        while (!pending.isEmpty()) {
            String code = pending.poll();
            if (!reachable.add(code)) continue;
            for (Skip skip : incoming.getOrDefault(code, List.of())) pending.add(skip.getNowNodeCode());
        }
        return reachable;
    }

    /**
     * 判断从起始节点沿通过线能否到达目标节点（用于排他网关选路，忽略节点类型，
     * 后续区间遍历会再校验节点是否允许被跳过）。
     *
     * @param fromCode 起始节点编码
     * @param toCode   目标节点编码
     * @param allSkips 全部连线
     * @return 可达返回 {@code true}
     */
    private static boolean reaches(String fromCode, String toCode, List<Skip> allSkips) {
        Set<String> visited = new HashSet<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        queue.add(fromCode);
        while (!queue.isEmpty()) {
            String code = queue.poll();
            if (!visited.add(code)) {
                continue;
            }
            if (toCode.equals(code)) {
                return true;
            }
            for (Skip skip : allSkips) {
                if (code.equals(skip.getNowNodeCode()) && SkipType.isPass(skip.getSkipType())) {
                    queue.add(skip.getNextNodeCode());
                }
            }
        }
        return false;
    }


    /**
     * 判断 target → source 的任意 PASS 路径是否经过并行 / 包容网关。
     * <p>这里只识别网关区间；是否重走取决于驳回节点是否仍在未完整汇聚的分支内。</p>
     *
     * @param combine    流程组合数据
     * @param targetCode 返回目标节点
     * @param sourceCode 驳回发起节点
     * @return 经过并行 / 包容网关返回 {@code true}
     */
    public static boolean crossesParallelGateway(FlowCombine combine, String targetCode, String sourceCode) {
        if (StringUtils.isEmpty(targetCode) || StringUtils.isEmpty(sourceCode)) {
            return false;
        }
        Map<String, List<Skip>> passEdges = combine.getAllSkips().stream()
            .filter(s -> SkipType.isPass(s.getSkipType()))
            .collect(java.util.stream.Collectors.groupingBy(Skip::getNowNodeCode));
        Map<String, Node> nodes = StreamUtils.toMap(combine.getAllNodes(), Node::getNodeCode, node -> node);
        Set<String> plain = new HashSet<>();
        Set<String> crossed = new HashSet<>();
        java.util.ArrayDeque<String[]> queue = new java.util.ArrayDeque<>();
        queue.add(new String[]{targetCode, "0"});
        while (!queue.isEmpty()) {
            String[] state = queue.poll();
            String code = state[0];
            boolean hasGateway = "1".equals(state[1]);
            Set<String> seen = hasGateway ? crossed : plain;
            if (!seen.add(code)) {
                continue;
            }
            Node node = nodes.get(code);
            if (node != null && (NodeType.isGateWayParallel(node.getNodeType())
                || NodeType.isGateWayInclusive(node.getNodeType()))) {
                hasGateway = true;
            }
            if (sourceCode.equals(code)) {
                if (hasGateway) {
                    return true;
                }
                continue;
            }
            for (Skip skip : passEdges.getOrDefault(code, List.of())) {
                queue.add(new String[]{skip.getNextNodeCode(), hasGateway ? "1" : "0"});
            }
        }
        return false;
    }

    /**
     * 判断节点是否位于并行 / 包容网关的分支内（可直接驳回直达的场景必须排除）。
     * <p>
     * 分叉仅在全部通过路径共同必经的并行/包容汇聚处闭合；部分汇聚和循环入口不会抵消外层分叉。
     * 从分叉出口遍历到完整汇聚之前仍属于分支，单入单出网关不闭合分支。
     *
     * @param combine  流程组合数据
     * @param nodeCode 待判断节点编码
     * @return 位于并行/包容分支内返回 {@code true}
     */
    public static boolean inParallelBranch(FlowCombine combine, String nodeCode) {
        if (StringUtils.isEmpty(nodeCode)) {
            return false;
        }
        return parallelBranchNodes(combine).contains(nodeCode);
    }

    private static Set<String> parallelBranchNodes(FlowCombine combine) {
        Map<String, List<Skip>> incoming = combine.getAllSkips().stream()
            .filter(s -> SkipType.isPass(s.getSkipType()))
            .collect(java.util.stream.Collectors.groupingBy(Skip::getNextNodeCode));
        Map<String, List<Skip>> outgoing = combine.getAllSkips().stream()
            .filter(s -> SkipType.isPass(s.getSkipType()))
            .collect(java.util.stream.Collectors.groupingBy(Skip::getNowNodeCode));
        Map<String, Node> nodes = StreamUtils.toMap(combine.getAllNodes(), Node::getNodeCode, node -> node);
        List<Node> forks = new ArrayList<>();
        for (Node node : nodes.values()) {
            if ((NodeType.isGateWayParallel(node.getNodeType()) || NodeType.isGateWayInclusive(node.getNodeType()))
                && outgoing.getOrDefault(node.getNodeCode(), List.of()).size() > 1) {
                forks.add(node);
            }
        }
        Set<String> branchNodes = new HashSet<>();
        if (forks.isEmpty()) {
            return branchNodes;
        }
        Map<String, Set<String>> dominators = postDominators(nodes, incoming, outgoing, Set.of(), true, false);
        Map<String, Set<String>> terminalDominators = null;
        for (Node fork : forks) {
            String forkCode = fork.getNodeCode();
            Map<String, Set<String>> current = dominators;
            if (current.get(forkCode).isEmpty()) {
                Map<String, Set<String>> entries = postDominators(nodes, outgoing, incoming, Set.of(forkCode), false, true);
                Set<String> headers = new HashSet<>();
                for (Skip skip : combine.getAllSkips()) {
                    if (SkipType.isPass(skip.getSkipType())
                        && entries.getOrDefault(skip.getNowNodeCode(), Set.of()).contains(skip.getNextNodeCode())) {
                        headers.add(skip.getNextNodeCode());
                    }
                }
                Set<String> exits = new HashSet<>();
                for (String header : headers) {
                    boolean nested = headers.stream().anyMatch(other -> !header.equals(other)
                        && entries.getOrDefault(header, Set.of()).contains(other));
                    boolean shared = outgoing.get(forkCode).stream().allMatch(skip ->
                        reaches(skip.getNextNodeCode(), header, combine.getAllSkips()));
                    if (!nested && shared) {
                        exits.add(header);
                    }
                }
                if (!exits.isEmpty()) {
                    current = postDominators(nodes, incoming, outgoing, exits, true, false);
                } else {
                    if (terminalDominators == null) {
                        terminalDominators = postDominators(nodes, incoming, outgoing,
                            terminalComponentExits(nodes, incoming, outgoing), true, false);
                    }
                    current = terminalDominators;
                }
            }
            Set<String> common = null;
            for (Skip skip : outgoing.get(forkCode)) {
                Set<String> next = current.getOrDefault(skip.getNextNodeCode(), Set.of());
                if (common == null) {
                    common = new HashSet<>(next);
                } else {
                    common.retainAll(next);
                }
            }
            Set<String> joins = new HashSet<>();
            for (String code : common) {
                Node node = nodes.get(code);
                if (!forkCode.equals(code)
                    && (NodeType.isGateWayParallel(node.getNodeType()) || NodeType.isGateWayInclusive(node.getNodeType()))
                    && incoming.getOrDefault(code, List.of()).size() > 1) {
                    joins.add(code);
                }
            }
            Set<String> visited = new HashSet<>();
            java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
            for (Skip skip : outgoing.get(forkCode)) {
                if (nodes.containsKey(skip.getNextNodeCode())) {
                    queue.add(skip.getNextNodeCode());
                }
            }
            while (!queue.isEmpty()) {
                String code = queue.poll();
                if (!visited.add(code) || forkCode.equals(code) || joins.contains(code)) {
                    continue;
                }
                branchNodes.add(code);
                for (Skip skip : outgoing.getOrDefault(code, List.of())) {
                    if (nodes.containsKey(skip.getNextNodeCode())) {
                        queue.add(skip.getNextNodeCode());
                    }
                }
            }
        }
        return branchNodes;
    }

    private static Set<String> terminalComponentExits(Map<String, Node> nodes,
        Map<String, List<Skip>> incoming, Map<String, List<Skip>> outgoing) {
        Set<String> visited = new HashSet<>();
        List<String> order = new ArrayList<>();
        ArrayDeque<String> path = new ArrayDeque<>();
        ArrayDeque<Iterator<Skip>> successors = new ArrayDeque<>();
        for (String code : nodes.keySet()) {
            if (!visited.add(code)) {
                continue;
            }
            path.push(code);
            successors.push(outgoing.getOrDefault(code, List.of()).iterator());
            while (!path.isEmpty()) {
                Iterator<Skip> iterator = successors.peek();
                if (!iterator.hasNext()) {
                    order.add(path.pop());
                    successors.pop();
                    continue;
                }
                String next = iterator.next().getNextNodeCode();
                if (nodes.containsKey(next) && visited.add(next)) {
                    path.push(next);
                    successors.push(outgoing.getOrDefault(next, List.of()).iterator());
                }
            }
        }
        Set<String> assigned = new HashSet<>();
        Set<String> exits = new HashSet<>();
        for (int index = order.size() - 1; index >= 0; index--) {
            String code = order.get(index);
            if (!assigned.add(code)) {
                continue;
            }
            Set<String> component = new HashSet<>();
            path.push(code);
            while (!path.isEmpty()) {
                String current = path.pop();
                component.add(current);
                for (Skip skip : incoming.getOrDefault(current, List.of())) {
                    String previous = skip.getNowNodeCode();
                    if (nodes.containsKey(previous) && assigned.add(previous)) {
                        path.push(previous);
                    }
                }
            }
            boolean terminal = component.stream().allMatch(current -> outgoing.getOrDefault(current, List.of()).stream()
                .allMatch(skip -> component.contains(skip.getNextNodeCode())));
            if (terminal) {
                exits.addAll(component);
            }
        }
        return exits;
    }

    private static Map<String, Set<String>> postDominators(Map<String, Node> nodes,
        Map<String, List<Skip>> incoming, Map<String, List<Skip>> outgoing, Set<String> extraExits,
        boolean includeEnds, boolean reverse) {
        Set<String> exits = new HashSet<>(extraExits);
        if (includeEnds) {
            for (String code : nodes.keySet()) {
                if (outgoing.getOrDefault(code, List.of()).isEmpty()) {
                    exits.add(code);
                }
            }
        }
        Set<String> reachesExit = new LinkedHashSet<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>(exits);
        while (!queue.isEmpty()) {
            String code = queue.poll();
            if (!reachesExit.add(code)) {
                continue;
            }
            for (Skip skip : incoming.getOrDefault(code, List.of())) {
                String previous = reverse ? skip.getNextNodeCode() : skip.getNowNodeCode();
                if (nodes.containsKey(previous)) {
                    queue.add(previous);
                }
            }
        }
        Map<String, Set<String>> dominators = new HashMap<>();
        for (String code : nodes.keySet()) {
            Set<String> initial = new HashSet<>();
            if (reachesExit.contains(code)) {
                if (exits.contains(code)) {
                    initial.add(code);
                } else {
                    initial.addAll(reachesExit);
                }
            }
            dominators.put(code, initial);
        }
        queue.addAll(reachesExit);
        Set<String> queued = new HashSet<>(reachesExit);
        while (!queue.isEmpty()) {
            String code = queue.poll();
            queued.remove(code);
            List<Skip> successors = outgoing.getOrDefault(code, List.of());
            if (successors.isEmpty() || exits.contains(code)) {
                continue;
            }
            Set<String> shared = null;
            for (Skip skip : successors) {
                String nextCode = reverse ? skip.getNowNodeCode() : skip.getNextNodeCode();
                if (reverse && !reachesExit.contains(nextCode)) {
                    continue;
                }
                Set<String> next = dominators.getOrDefault(nextCode, Set.of());
                if (shared == null) {
                    shared = new HashSet<>(next);
                } else {
                    shared.retainAll(next);
                }
            }
            if (shared == null) {
                continue;
            }
            shared.add(code);
            if (!shared.equals(dominators.get(code))) {
                dominators.put(code, shared);
                for (Skip skip : incoming.getOrDefault(code, List.of())) {
                    String previous = reverse ? skip.getNextNodeCode() : skip.getNowNodeCode();
                    if (reachesExit.contains(previous) && queued.add(previous)) {
                        queue.add(previous);
                    }
                }
            }
        }
        return dominators;
    }

    /**
     * 校验多人审批办理顺序字段；空值保留给旧定义并按并行或旧后缀兼容读取。
     *
     * @param node 待校验节点
     */
    private static void checkCooperateType(Node node) {
        String cooperateType = node.getCooperateType();
        if (StringUtils.isEmpty(cooperateType)) {
            return;
        }
        AssertUtil.isTrue(!CooperateType.PARALLEL.equalsIgnoreCase(cooperateType)
                && !CooperateType.SEQUENCE.equalsIgnoreCase(cooperateType)
                && !CooperateType.isGroup(cooperateType)
            , "[" + node.getNodeCode() + "]协作顺序仅支持 parallel、sequence 或 group");
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
     * 发布组签能力门禁；草稿保存与导入只校验 {@code cooperateType} 取值。
     *
     * @param combine 含定义、节点和连线的流程组合数据
     */
    public static void validateGroupSignCapability(FlowCombine combine) {
        for (Node node : combine.getAllNodes()) {
            if (!CooperateType.isGroup(node.getCooperateType())) {
                continue;
            }
            String prefix = "[" + node.getNodeCode() + "]";
            AssertUtil.isTrue(!NodeType.isBetween(node.getNodeType()),
                prefix + "组签只能配置在普通人工 BETWEEN 节点");
            AssertUtil.isTrue(StringUtils.isNotEmpty(node.getEventType()), prefix + "事件节点不能配置组签");
            AssertUtil.isTrue(StringUtils.isNotEmpty(node.getPermissionFlag()), prefix + "组签节点办理人权限必须为空");
            AssertUtil.isTrue(!CooperateType.isOrSign(node.getNodeRatio()), prefix + "组签节点比例必须为 0");

            GroupSignConfig config = GroupSignUtil.require(node);
            List<GroupSignConfig.Group> groups = config.getGroups();
            AssertUtil.isTrue(CollUtil.isEmpty(groups), prefix + "组签至少需要一个审批组");
            boolean legalOrder = CooperateType.PARALLEL.equalsIgnoreCase(config.getOrder())
                || CooperateType.SEQUENCE.equalsIgnoreCase(config.getOrder());
            AssertUtil.isTrue(!legalOrder, prefix + "组签顺序只能是 parallel 或 sequence");

            Set<String> ids = new HashSet<>();
            for (GroupSignConfig.Group group : groups) {
                String id = group.getId();
                AssertUtil.isTrue(StringUtils.isEmpty(id) || !id.matches("[A-Za-z0-9_-]{1,32}"),
                    prefix + "组标识必须匹配 [A-Za-z0-9_-]{1,32}");
                AssertUtil.contains(ids, id, prefix + "组标识不能重复: " + id);
                ids.add(id);
                boolean hasMember = CollUtil.isNotEmpty(group.getMembers())
                    && group.getMembers().stream().anyMatch(StringUtils::isNotEmpty);
                AssertUtil.isTrue(!hasMember, prefix + "组 " + id + " 的成员不能为空");
            }
        }
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

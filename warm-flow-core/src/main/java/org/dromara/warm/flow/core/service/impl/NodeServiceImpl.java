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
import org.dromara.warm.flow.core.constant.FlowCons;
import org.dromara.warm.flow.core.dto.FlowCombine;
import org.dromara.warm.flow.core.dto.PathWayData;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Skip;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.orm.dao.FlowNodeDao;
import org.dromara.warm.flow.core.orm.service.impl.WarmServiceImpl;
import org.dromara.warm.flow.core.service.NodeService;
import org.dromara.warm.flow.core.strategy.GatewayStrategy;
import org.dromara.warm.flow.core.strategy.gateway.InclusiveGatewayStrategy;
import org.dromara.warm.flow.core.strategy.gateway.ParallelGatewayStrategy;
import org.dromara.warm.flow.core.strategy.gateway.SerialGatewayStrategy;
import org.dromara.warm.flow.core.utils.*;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 流程节点服务实现。
 *
 * <p>除基础节点查询外，还负责沿连线解析前后节点、选择普通出口，并递归穿透串行、并行和包容网关。</p>
 *
 * @author warm
 * @since 2023-03-29
 */
@Slf4j
public class NodeServiceImpl extends WarmServiceImpl<FlowNodeDao<Node>, Node> implements NodeService {

    /**
     * 按网关类型选择出口的策略。
     */
    private final List<GatewayStrategy> gatewayStrategies = List.of(
        new SerialGatewayStrategy(), new ParallelGatewayStrategy(), new InclusiveGatewayStrategy());

    /**
     * 注入流程节点 DAO。
     *
     * @param warmDao 流程节点数据访问对象
     * @return 当前服务实例
     */
    @Override
    public NodeService setDao(FlowNodeDao<Node> warmDao) {
        this.warmDao = warmDao;
        return this;
    }

    /**
     * 按流程编码查询当前已发布版本的全部节点。
     *
     * @param flowCode 流程编码
     * @return 已发布流程定义的节点集合，不存在已发布版本时返回空集合
     */
    @Override
    public List<Node> getPublishByFlowCode(String flowCode) {
        Definition definition = FlowEngine.defService().getOne(FlowEngine.newDef()
            .setFlowCode(flowCode).setIsPublish(PublishStatus.PUBLISHED.getKey()));
        if (ObjectUtil.isNotNull(definition)) {
            return list(FlowEngine.newNode().setDefinitionId(definition.getId()));
        }
        return List.of();
    }

    /**
     * 按流程定义和节点编码集合批量查询节点。
     *
     * @param nodeCodes    节点编码集合
     * @param definitionId 流程定义主键
     * @return 命中的节点集合
     */
    @Override
    public List<Node> getByNodeCodes(List<String> nodeCodes, Long definitionId) {
        return getDao().getByNodeCodes(nodeCodes, definitionId);
    }

    /**
     * 按节点主键查询所有可追溯的前置业务节点，结果不包含网关。
     *
     * @param nodeId 当前节点主键
     * @return 前置业务节点集合
     */
    @Override
    public List<Node> previousNodeList(Long nodeId) {
        Node nowNode = getById(nodeId);
        return previousNodeList(nowNode.getDefinitionId(), nowNode.getNodeCode());
    }

    /**
     * 按流程定义和节点编码查询所有前置业务节点。
     *
     * @param definitionId 流程定义主键
     * @param nowNodeCode  当前节点编码
     * @return 前置业务节点集合
     */
    @Override
    public List<Node> previousNodeList(Long definitionId, String nowNodeCode) {
        return prefixOrSuffixNodes(definitionId, nowNodeCode, FlowCons.PREVIOUS);
    }

    /**
     * 基于已加载的流程组合查询所有前置业务节点，避免重复访问数据库。
     *
     * @param nowNodeCode 当前节点编码
     * @param flowCombine 已加载的流程组合数据
     * @return 前置业务节点集合
     */
    @Override
    public List<Node> previousNodeList(String nowNodeCode, FlowCombine flowCombine) {
        return prefixOrSuffixNodes(nowNodeCode, FlowCons.PREVIOUS, flowCombine);
    }

    /**
     * 按节点主键查询所有可到达的后置业务节点，结果不包含网关。
     *
     * @param nodeId 当前节点主键
     * @return 后置业务节点集合
     */
    @Override
    public List<Node> suffixNodeList(Long nodeId) {
        Node nowNode = getById(nodeId);
        return suffixNodeList(nowNode.getDefinitionId(), nowNode.getNodeCode());
    }

    /**
     * 按流程定义和节点编码查询所有后置业务节点。
     *
     * @param definitionId 流程定义主键
     * @param nowNodeCode  当前节点编码
     * @return 后置业务节点集合
     */
    @Override
    public List<Node> suffixNodeList(Long definitionId, String nowNodeCode) {
        return prefixOrSuffixNodes(definitionId, nowNodeCode, FlowCons.SUFFIX);
    }

    /**
     * 基于已加载的流程组合查询所有后置业务节点。
     *
     * @param nowNodeCode 当前节点编码
     * @param flowCombine 已加载的流程组合数据
     * @return 后置业务节点集合
     */
    @Override
    public List<Node> suffixNodeList(String nowNodeCode, FlowCombine flowCombine) {
        return prefixOrSuffixNodes(nowNodeCode, FlowCons.SUFFIX, flowCombine);
    }

    /**
     * 按流程定义主键查询全部节点。
     *
     * @param definitionId 流程定义主键
     * @return 流程节点集合
     */
    @Override
    public List<Node> getByDefId(Long definitionId) {
        return list(FlowEngine.newNode().setDefinitionId(definitionId));
    }

    /**
     * 按流程定义主键和节点编码查询唯一节点。
     *
     * @param definitionId 流程定义主键
     * @param nodeCode     节点编码
     * @return 匹配节点，不存在时返回 {@code null}
     */
    @Override
    public Node getByDefIdAndNodeCode(Long definitionId, String nodeCode) {
        return getOne(FlowEngine.newNode().setDefinitionId(definitionId).setNodeCode(nodeCode));
    }

    /**
     * 查询流程定义的开始节点。
     *
     * @param definitionId 流程定义主键
     * @return 开始节点
     */
    @Override
    public Node getStartNode(Long definitionId) {
        return getOne(FlowEngine.newNode().setDefinitionId(definitionId).setNodeType(NodeType.START.getKey()));
    }

    /**
     * 查询流程定义的全部中间业务节点。
     *
     * @param definitionId 流程定义主键
     * @return 中间业务节点集合
     */
    @Override
    public List<Node> getBetweenNode(Long definitionId) {
        return list(FlowEngine.newNode().setDefinitionId(definitionId).setNodeType(NodeType.BETWEEN.getKey()));
    }


    /**
     * 从开始节点出发解析首批可办理节点，流程变量用于判断条件和网关出口。
     *
     * @param definitionId 流程定义主键
     * @param variable     流程变量
     * @return 首批可办理节点
     */
    @Override
    public List<Node> getFirstBetweenNode(Long definitionId, Map<String, Object> variable) {
        FlowCombine flowCombine = FlowEngine.defService().getFlowCombineNoDef(definitionId);
        Node startNode = StreamUtils.filterOne(flowCombine.getAllNodes(), t -> NodeType.isStart(t.getNodeType()));
        return getNextNodeList(startNode, null, SkipType.PASS.getKey(),
            variable, null, flowCombine);
    }

    /**
     * 查询流程定义的结束节点。
     *
     * @param definitionId 流程定义主键
     * @return 结束节点
     */
    @Override
    public Node getEndNode(Long definitionId) {
        return getOne(FlowEngine.newNode().setDefinitionId(definitionId).setNodeType(NodeType.END.getKey()));
    }

    /**
     * 加载流程节点和连线后，查询当前节点的全部前置或后置业务节点。
     *
     * @param definitionId 流程定义主键
     * @param nowNodeCode  当前节点编码
     * @param type         查询方向
     * @return 前置或后置业务节点集合
     */
    public List<Node> prefixOrSuffixNodes(Long definitionId, String nowNodeCode, String type) {
        FlowCombine flowCombine = new FlowCombine();
        flowCombine.setAllNodes(FlowEngine.nodeService().getByDefId(definitionId));
        flowCombine.setAllSkips(FlowEngine.skipService().getByDefId(definitionId));
        return prefixOrSuffixNodes(nowNodeCode, type, flowCombine);
    }

    /**
     * 基于流程组合遍历前置或后置路径，过滤网关并按最终访问顺序去重。
     *
     * @param nowNodeCode 当前节点编码
     * @param type        {@link FlowCons#PREVIOUS} 或 {@link FlowCons#SUFFIX}
     * @param flowCombine 已加载的流程节点和连线
     * @return 路径上的业务节点
     */
    public List<Node> prefixOrSuffixNodes(String nowNodeCode, String type, FlowCombine flowCombine) {
        GraphIndex graphIndex = GraphIndex.of(flowCombine);
        Map<String, List<Skip>> skipMap = flowCombine.getAllSkips().stream().filter(skip -> SkipType.isPass(skip.getSkipType()))
            .collect(Collectors.groupingBy(FlowCons.PREVIOUS.equals(type) ? Skip::getNextNodeCode : Skip::getNowNodeCode
                , LinkedHashMap::new, Collectors.toList()));

        List<Node> prefixOrSuffixNodes = new ArrayList<>();
        List<String> prefixOrSuffixCode = prefixOrSuffixCodes(skipMap, nowNodeCode
            , FlowCons.PREVIOUS.equals(type) ? Skip::getNowNodeCode : Skip::getNextNodeCode);
        for (String nodeCode : prefixOrSuffixCode) {
            Node node = graphIndex.node(nodeCode);
            AssertUtil.isNull(node, ExceptionCons.NULL_NODE_CODE);
            if (!NodeType.isGateWay(node.getNodeType())) {
                prefixOrSuffixNodes.add(node);
            }
        }
        Collections.reverse(prefixOrSuffixNodes);
        Set<String> sameCode = new HashSet<>();
        prefixOrSuffixNodes.removeIf(node -> {
            if (sameCode.contains(node.getNodeCode())) {
                return true;
            }
            sameCode.add(node.getNodeCode());
            return false;
        });
        Collections.reverse(prefixOrSuffixNodes);
        return prefixOrSuffixNodes;
    }

    /**
     * 加载流程结构并解析一次流转最终到达的业务节点列表。
     *
     * @param definitionId 流程定义主键
     * @param nowNodeCode  当前节点编码
     * @param anyNodeCode  指定目标节点编码
     * @param skipType     跳转类型
     * @param variable     流程变量
     * @return 最终到达的业务节点集合
     */
    @Override
    public List<Node> getNextNodeList(Long definitionId, String nowNodeCode, String anyNodeCode, String skipType,
                                      Map<String, Object> variable) {
        AssertUtil.isEmpty(nowNodeCode, ExceptionCons.LOST_NODE_CODE);
        // 查询当前节点
        FlowCombine flowCombine = FlowEngine.defService().getFlowCombineNoDef(definitionId);
        Node nowNode = StreamUtils.filterOne(flowCombine.getAllNodes(), t -> t.getNodeCode().equals(nowNodeCode));
        // 如果是网关节点，则根据条件判断
        return getNextByCheckGateway(variable, getNextNode(nowNode, anyNodeCode, skipType, null, flowCombine),
            null, flowCombine);
    }

    /**
     * 加载流程结构并选择当前节点的直接下一节点；该方法不递归穿透网关。
     *
     * @param definitionId 流程定义主键
     * @param nowNodeCode  当前节点编码
     * @param anyNodeCode  指定目标节点编码
     * @param skipType     跳转类型
     * @return 直接下一节点
     */
    @Override
    public Node getNextNode(Long definitionId, String nowNodeCode, String anyNodeCode, String skipType) {
        // 查询当前节点
        FlowCombine flowCombine = FlowEngine.defService().getFlowCombineNoDef(definitionId);
        Node nowNode = StreamUtils.filterOne(flowCombine.getAllNodes(), t -> t.getNodeCode().equals(nowNodeCode));
        return getNextNode(nowNode, anyNodeCode, skipType, null, flowCombine);
    }

    /**
     * 基于已加载流程组合选择直接下一节点，并递归解析网关后的业务节点。
     *
     * @param nowNode     当前节点
     * @param anyNodeCode 指定目标节点编码
     * @param skipType    跳转类型
     * @param variable    流程变量
     * @param pathWayData 路径收集器，可为空
     * @param flowCombine 已加载的流程组合数据
     * @return 最终到达的业务节点集合
     */
    @Override
    public List<Node> getNextNodeList(Node nowNode, String anyNodeCode, String skipType, Map<String, Object> variable
        , PathWayData pathWayData, FlowCombine flowCombine) {
        GraphIndex graphIndex = GraphIndex.of(flowCombine);
        // 如果是网关节点，则根据条件判断
        return getNextByCheckGateway(variable, getNextNode(nowNode, anyNodeCode, skipType
            , pathWayData, graphIndex), pathWayData, graphIndex, new LinkedHashSet<>());
    }

    /**
     * 选择当前节点的一条直接出口。
     *
     * <p>显式指定节点优先，其次使用驳回任意跳转配置，最后按跳转类型匹配普通连线；路径收集器非空时同步记录节点和连线。</p>
     *
     * @param nowNode     当前节点
     * @param anyNodeCode 指定目标节点编码
     * @param skipType    跳转类型
     * @param pathWayData 路径收集器，可为空
     * @param flowCombine 已加载的流程组合数据
     * @return 直接下一节点
     */
    @Override
    public Node getNextNode(Node nowNode, String anyNodeCode, String skipType, PathWayData pathWayData, FlowCombine flowCombine) {
        return getNextNode(nowNode, anyNodeCode, skipType, pathWayData, GraphIndex.of(flowCombine));
    }

    private Node getNextNode(Node nowNode, String anyNodeCode, String skipType, PathWayData pathWayData
        , GraphIndex graphIndex) {
        // 查询当前节点
        AssertUtil.isNull(nowNode, ExceptionCons.LOST_NODE_CODE);
        AssertUtil.isNull(nowNode.getDefinitionId(), ExceptionCons.NOT_DEFINITION_ID);
        AssertUtil.isEmpty(skipType, ExceptionCons.NULL_CONDITION_VALUE);

        if (pathWayData != null) {
            pathWayData.getPathWayNodes().add(nowNode);
        }
        Node nextNode = null;
        if (StringUtils.isNotEmpty(anyNodeCode)) {
            // 如果指定了跳转节点，直接获取节点
            nextNode = graphIndex.node(anyNodeCode);
        } else if (StringUtils.isNotEmpty(nowNode.getAnyNodeSkip()) && SkipType.isReject(skipType)) {
            // 如果配置了任意跳转节点，直接获取节点
            nextNode = graphIndex.node(nowNode.getAnyNodeSkip());
        }

        if (ObjectUtil.isNotNull(nextNode)) {
            AssertUtil.isTrue(NodeType.isGateWay(nextNode.getNodeType()), ExceptionCons.TAR_NOT_GATEWAY);
            return nextNode;
        }

        // 获取跳转关系
        List<Skip> skips = graphIndex.outgoing(nowNode.getNodeCode());
        AssertUtil.isNull(skips, ExceptionCons.NULL_DEST_NODE);
        Skip nextSkip = getSkipByCheck(skips, skipType);

        // 根据跳转查询出跳转到的那个节点
        nextNode = graphIndex.node(nextSkip.getNextNodeCode());
        AssertUtil.isNull(nextNode, ExceptionCons.NULL_NODE_CODE);
        AssertUtil.isTrue(NodeType.isStart(nextNode.getNodeType()), ExceptionCons.FIRST_FORBID_BACK);
        if (pathWayData != null) {
            pathWayData.getPathWayNodes().add(nextNode);
            pathWayData.getPathWaySkips().add(nextSkip);
        }
        return nextNode;
    }

    /**
     * 递归穿透网关并返回最终业务节点。
     *
     * <p>每个网关先由对应策略选择生效出口，出口仍为网关时继续递归，直到到达普通节点。</p>
     *
     * @param variable    流程变量
     * @param nextNode    待解析节点
     * @param pathWayData 路径收集器，可为空
     * @param flowCombine 已加载的流程组合数据
     * @return 最终业务节点集合
     */
    @Override
    public List<Node> getNextByCheckGateway(Map<String, Object> variable, Node nextNode, PathWayData pathWayData
        , FlowCombine flowCombine) {
        return getNextByCheckGateway(variable, nextNode, pathWayData, GraphIndex.of(flowCombine)
            , new LinkedHashSet<>());
    }

    private List<Node> getNextByCheckGateway(Map<String, Object> variable, Node nextNode, PathWayData pathWayData
        , GraphIndex graphIndex, Set<String> gatewayPath) {
        // 网关节点处理
        if (NodeType.isGateWay(nextNode.getNodeType())) {
            String gatewayCode = nextNode.getNodeCode();
            AssertUtil.isTrue(!gatewayPath.add(gatewayCode), ExceptionCons.GATEWAY_CYCLE);
            List<Skip> skipsGateway = graphIndex.outgoing(gatewayCode);
            try {
                if (CollUtil.isEmpty(skipsGateway)) {
                    return null;
                }

                skipsGateway = selectGatewaySkips(nextNode.getNodeType(), skipsGateway, variable);

                AssertUtil.isEmpty(skipsGateway, ExceptionCons.NULL_CONDITION_VALUE_NODE);
                List<Node> nextNodes = graphIndex.nodes(StreamUtils.toList(skipsGateway, Skip::getNextNodeCode));
                AssertUtil.isEmpty(nextNodes, ExceptionCons.NOT_NODE_DATA);
                if (pathWayData != null) {
                    pathWayData.getPathWayNodes().addAll(nextNodes);
                    pathWayData.getPathWaySkips().addAll(skipsGateway);
                }
                List<Node> newNextNodes = new ArrayList<>();
                for (Node node : nextNodes) {
                    List<Node> nodeList = getNextByCheckGateway(variable, node, pathWayData, graphIndex, gatewayPath);
                    newNextNodes.addAll(nodeList);
                }
                return newNextNodes;
            } finally {
                gatewayPath.remove(gatewayCode);
            }
        }
        // 非网关节点直接返回
        if (pathWayData != null) {
            pathWayData.getPathWayNodes().remove(nextNode);
        }
        AssertUtil.isTrue(NodeType.isStart(nextNode.getNodeType()), ExceptionCons.START_NODE_NOT_ALLOW_JUMP);
        return CollUtil.toList(nextNode);
    }

    /**
     * 委托对应网关策略选择实际生效的出口。
     *
     * @param nodeType 网关节点类型
     * @param skips    网关出口
     * @param variable 流程变量
     * @return 生效出口
     */
    private List<Skip> selectGatewaySkips(Integer nodeType, List<Skip> skips, Map<String, Object> variable) {
        for (GatewayStrategy strategy : gatewayStrategies) {
            if (strategy.supports(nodeType)) {
                return strategy.select(skips, variable);
            }
        }
        return List.of();
    }


    /**
     * 批量删除指定流程定义下的全部节点。
     *
     * @param defIds 流程定义主键集合
     * @return 受影响行数
     */
    @Override
    public int deleteNodeByDefIds(Collection<? extends Serializable> defIds) {
        return getDao().deleteNodeByDefIds(defIds);
    }

    /**
     * 将节点扩展属性 JSON 转换为编码到值的映射，忽略编码或值为空的条目。
     *
     * @param node 流程节点
     * @return 扩展属性映射
     */
    @Override
    public Map<String, String> getExt(Node node) {
        Map<String, String> map = new HashMap<>();
        String ext = node.getExt();
        if (StringUtils.isNotEmpty(ext)) {
            List<Map<String, Object>> extList = FlowEngine.jsonConvert.strToList(ext);
            if (CollUtil.isNotEmpty(extList)) {
                for (Map<String, Object> extMap : extList) {
                    String code = ObjectUtil.defaultNull(extMap.get("code"), "").toString();
                    String value = ObjectUtil.defaultNull(extMap.get("value"), "").toString();
                    if (StringUtils.isAllNotEmpty(code, value)) {
                        map.put(code, value);
                    }
                }
            }
        }

        return map;
    }

    /**
     * 遍历连线图并收集前置或后置节点编码。
     *
     * @param skipMap  按当前方向分组的连线
     * @param nodeCode 起始节点编码
     * @param supplier 从连线提取下一节点编码的函数
     * @return 节点编码集合
     */
    private List<String> prefixOrSuffixCodes(Map<String, List<Skip>> skipMap, String nodeCode,
                                             Function<Skip, String> supplier) {
        Set<String> visited = new HashSet<>();
        Set<String> result = new LinkedHashSet<>();
        Deque<Iterator<Skip>> stack = new ArrayDeque<>();
        visited.add(nodeCode);
        stack.push(skipMap.getOrDefault(nodeCode, List.of()).iterator());
        while (!stack.isEmpty()) {
            Iterator<Skip> iterator = stack.peek();
            if (!iterator.hasNext()) {
                stack.pop();
                continue;
            }
            String nextNodeCode = supplier.apply(iterator.next());
            result.add(nextNodeCode);
            if (visited.add(nextNodeCode)) {
                stack.push(skipMap.getOrDefault(nextNodeCode, List.of()).iterator());
            }
        }
        return new ArrayList<>(result);
    }


    /**
     * 通过校验跳转类型获取跳转集合
     *
     * @param skips    跳转集合
     * @param skipType 跳转类型
     * @return List<Skip>
     * @author xiarg
     * @since 2024/8/21 11:32
     */
    private Skip getSkipByCheck(List<Skip> skips, String skipType) {
        return Optional.ofNullable(skips)
            .orElse(List.of())
            .stream()
            .filter(t -> StringUtils.isEmpty(t.getSkipType()) || skipType.equals(t.getSkipType()))
            .findFirst()
            .orElseThrow(() -> new FlowException(ExceptionCons.NULL_SKIP_TYPE));
    }

    /**
     * 单次路径解析使用的流程图索引，不跨操作缓存。
     */
    private record GraphIndex(Map<String, Node> nodes, Map<String, List<Skip>> outgoing) {

        private static GraphIndex of(FlowCombine flowCombine) {
            Map<String, Node> nodes = flowCombine.getAllNodes().stream().filter(Objects::nonNull)
                .collect(Collectors.toMap(Node::getNodeCode, node -> node, (left, right) -> left, LinkedHashMap::new));
            Map<String, List<Skip>> outgoing = flowCombine.getAllSkips().stream()
                .collect(Collectors.groupingBy(Skip::getNowNodeCode, LinkedHashMap::new, Collectors.toList()));
            return new GraphIndex(nodes, outgoing);
        }

        private Node node(String nodeCode) {
            return nodes.get(nodeCode);
        }

        private List<Node> nodes(List<String> nodeCodes) {
            Set<String> selectedCodes = new HashSet<>(nodeCodes);
            return nodes.values().stream().filter(node -> selectedCodes.contains(node.getNodeCode())).toList();
        }

        private List<Skip> outgoing(String nodeCode) {
            return outgoing.getOrDefault(nodeCode, List.of());
        }
    }
}

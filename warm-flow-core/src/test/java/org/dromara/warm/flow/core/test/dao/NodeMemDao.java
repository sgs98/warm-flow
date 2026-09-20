package org.dromara.warm.flow.core.test.dao;

import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.orm.dao.FlowNodeDao;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 单测内存实现：流程节点 DAO。
 *
 * @author warm
 */
public class NodeMemDao<T extends Node> extends InMemoryDao<T> implements FlowNodeDao<T> {

    /**
     * 创建流程节点内存 DAO，并复用基类的实体工厂与调用日志。
     */
    public NodeMemDao(Supplier<T> factory, List<String> log) {
        super(factory, log);
    }

    /**
     * 按流程定义 ID 与节点编码集合查询节点。
     */
    @Override
    public List<T> getByNodeCodes(List<String> nodeCodes, Long definitionId) {
        log.add(name() + ".getByNodeCodes[nodeCodes=" + nodeCodes + ",definitionId=" + definitionId + "]");
        return store.values().stream()
                .filter(n -> Objects.equals(((Node) n).getDefinitionId(), definitionId)
                        && nodeCodes.contains(((Node) n).getNodeCode()))
                .map(this::copyOf)
                .collect(Collectors.toList());
    }

    /**
     * 删除指定流程定义下的全部节点。
     */
    @Override
    public int deleteNodeByDefIds(Collection<? extends Serializable> defIds) {
        log.add(name() + ".deleteNodeByDefIds" + defIds);
        Set<Long> ids = defIds.stream().map(id -> Long.parseLong(String.valueOf(id))).collect(Collectors.toSet());
        List<Long> keys = store.values().stream().map(Node.class::cast)
                .filter(n -> ids.contains(n.getDefinitionId()))
                .map(Node::getId)
                .collect(Collectors.toList());
        keys.forEach(store::remove);
        return keys.size();
    }
}

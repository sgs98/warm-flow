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

    public NodeMemDao(Supplier<T> factory, List<String> log) {
        super(factory, log);
    }

    @Override
    public List<T> getByNodeCodes(List<String> nodeCodes, Long definitionId) {
        log.add(name() + ".getByNodeCodes[nodeCodes=" + nodeCodes + ",definitionId=" + definitionId + "]");
        return store.values().stream()
                .filter(n -> Objects.equals(((Node) n).getDefinitionId(), definitionId)
                        && nodeCodes.contains(((Node) n).getNodeCode()))
                .map(this::copyOf)
                .collect(Collectors.toList());
    }

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

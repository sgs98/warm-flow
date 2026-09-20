package org.dromara.warm.flow.core.test.dao;

import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.orm.dao.FlowHisTaskDao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 单测内存实现：历史任务 DAO。
 * getNoReject 语义对齐 FlowHisTaskMapper.xml：instance_id 等值 + skip_type='PASS'
 * 按 create_time 倒序——内存实现以插入序的逆序近似（测试数据创建时间单调递增）。
 *
 * @author warm
 */
public class HisTaskMemDao<T extends HisTask> extends InMemoryDao<T> implements FlowHisTaskDao<T> {

    /**
     * 创建历史任务内存 DAO，并复用基类的实体工厂与调用日志。
     */
    public HisTaskMemDao(Supplier<T> factory, List<String> log) {
        super(factory, log);
    }

    /**
     * 查询实例下未驳回的历史任务，并按最近创建顺序返回。
     */
    @Override
    public List<T> getNoReject(Long instanceId) {
        log.add(name() + ".getNoReject[instanceId=" + instanceId + "]");
        List<T> result = store.values().stream()
                .filter(h -> Objects.equals(((HisTask) h).getInstanceId(), instanceId))
                .filter(h -> "PASS".equals(((HisTask) h).getSkipType()))
                .map(this::copyOf)
                .collect(Collectors.toList());
        List<T> reversed = new ArrayList<>(result);
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    /**
     * 按实例和节点编码集合查询历史任务。
     */
    @Override
    public List<T> getByInsAndNodeCodes(Long instanceId, List<String> nodeCodes) {
        log.add(name() + ".getByInsAndNodeCodes[instanceId=" + instanceId + ",nodeCodes=" + nodeCodes + "]");
        return store.values().stream()
                .filter(h -> Objects.equals(((HisTask) h).getInstanceId(), instanceId)
                        && nodeCodes.contains(((HisTask) h).getNodeCode()))
                .map(this::copyOf)
                .collect(Collectors.toList());
    }

    /**
     * 删除指定流程实例关联的全部历史任务。
     */
    @Override
    public int deleteByInsIds(List<Long> instanceIds) {
        log.add(name() + ".deleteByInsIds" + instanceIds);
        List<Long> keys = store.values().stream().map(HisTask.class::cast)
                .filter(h -> instanceIds.contains(h.getInstanceId()))
                .map(HisTask::getId)
                .collect(Collectors.toList());
        keys.forEach(store::remove);
        return keys.size();
    }

    /**
     * 按任务 ID 和协作类型集合筛选历史任务。
     */
    @Override
    public List<T> listByTaskIdAndCooperateTypes(Long taskId, Integer[] cooperateTypes) {
        log.add(name() + ".listByTaskIdAndCooperateTypes[taskId=" + taskId
                + ",cooperateTypes=" + Arrays.toString(cooperateTypes) + "]");
        return store.values().stream()
                .filter(h -> Objects.equals(((HisTask) h).getTaskId(), taskId))
                .filter(h -> Arrays.asList(cooperateTypes).contains(((HisTask) h).getCooperateType()))
                .map(this::copyOf)
                .collect(Collectors.toList());
    }
}

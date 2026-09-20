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
package org.dromara.warm.flow.core.orm.dao;

import org.dromara.warm.flow.core.entity.Node;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;


/**
 * 流程节点 DAO 接口。
 * <p>
 * 节点记录描述流程图中的开始、审批、网关、结束等节点。该接口提供节点编码维度的查询
 * 以及按流程定义批量清理节点的能力。
 *
 * @author warm
 * @since 2023-03-29
 */
public interface FlowNodeDao<T extends Node> extends WarmDao<T> {

    /**
     * 根据流程定义 ID 和节点编码集合查询节点。
     * <p>
     * 办理、退回、跳转、网关汇合等场景通过节点编码回查当前定义中的节点元数据。
     *
     * @param nodeCodes 节点编码集合
     * @param definitionId 流程定义 ID
     * @return 节点列表
     */
    List<T> getByNodeCodes(List<String> nodeCodes, Long definitionId);

    /**
     * 根据流程定义 ID 集合批量删除流程节点。
     *
     * @param defIds 流程定义 ID 集合
     * @return 受影响行数
     */
    public int deleteNodeByDefIds(Collection<? extends Serializable> defIds);
}

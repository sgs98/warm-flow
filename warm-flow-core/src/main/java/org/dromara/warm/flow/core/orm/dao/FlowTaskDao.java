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

import org.dromara.warm.flow.core.entity.Task;

import java.util.List;

/**
 * 待办任务 DAO 接口。
 * <p>
 * 待办任务表示实例当前可办理的节点任务。办理、终止、撤回、跳转、并行网关汇合等
 * 流程操作会通过该接口清理实例待办或按节点编码回查当前待办。
 *
 * @author warm
 * @since 2023-03-29
 */
public interface FlowTaskDao<T extends Task> extends WarmDao<T> {

    /**
     * 根据实例 ID 集合删除待办任务。
     *
     * @param instanceIds 流程实例 ID 集合
     * @return 受影响行数
     */
    int deleteByInsIds(List<Long> instanceIds);

    /**
     * 根据实例 ID 和节点编码集合查询待办任务。
     * <p>
     * 并行网关、任意跳转、退回路径判断等场景需要确认某些节点是否仍存在活动待办。
     *
     * @param instanceId 流程实例 ID
     * @param nodeCodes  节点编码集合
     * @return 待办任务列表
     */
    List<T> getByInsIdAndNodeCodes(Long instanceId, List<String> nodeCodes);
}

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

import org.dromara.warm.flow.core.entity.HisTask;

import java.util.List;

/**
 * 历史任务记录 DAO 接口。
 * <p>
 * 历史任务保存已办理、退回、转办、加签等操作轨迹。流程回退、撤回、协作投票统计等
 * 场景依赖这里的实例、节点和协作类型查询能力。
 *
 * @author warm
 * @since 2023-03-29
 */
public interface FlowHisTaskDao<T extends HisTask> extends WarmDao<T> {

    /**
     * 根据实例 ID 获取未退回的历史记录。
     * <p>
     * “未退回”通常指跳转类型为通过的历史轨迹，用于撤回、退回路径计算等只需要正向流转记录的场景。
     *
     * @param instanceId 流程实例 ID
     * @return 未退回的历史任务列表
     */
    List<T> getNoReject(Long instanceId);


    /**
     * 根据实例 ID 和节点编码集合查询历史记录。
     * <p>
     * 用于判断指定节点在某个实例中是否产生过历史任务，常见于路径回溯和并行网关判断。
     *
     * @param instanceId 流程实例 ID
     * @param nodeCodes  节点编码集合
     * @return 历史任务列表
     */
    List<T> getByInsAndNodeCodes(Long instanceId, List<String> nodeCodes);

    /**
     * 根据实例 ID 集合删除历史任务。
     *
     * @param instanceIds 流程实例 ID 集合
     * @return 受影响行数
     */
    int deleteByInsIds(List<Long> instanceIds);

    /**
     * 根据任务 ID 和协作类型集合查询历史任务。
     * <p>
     * 协作类型覆盖会签、票签、加签、转办等任务协作记录，服务层用该查询统计或回溯协作行为。
     *
     * @param taskId         待办任务 ID
     * @param cooperateTypes 协作类型集合
     * @return 历史任务列表
     */
    List<T> listByTaskIdAndCooperateTypes(Long taskId, Integer[] cooperateTypes);
}

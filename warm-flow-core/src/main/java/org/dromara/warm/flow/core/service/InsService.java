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
package org.dromara.warm.flow.core.service;

import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.orm.service.IWarmService;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

import java.util.List;
import java.util.Map;

/**
 * 流程实例Service接口
 *
 * @author warm
 * @since 2023-03-29
 */
public interface InsService extends IWarmService<Instance> {

    /**
     * 传入业务id开启流程
     *
     * @param businessId 业务主键
     * @param flowCode   流程定义编码
     * @param context    流程执行上下文
     * @return 流程实例
     */
    Instance start(String businessId, String flowCode, WorkflowContext context);

    /**
     * 按流程编码 + 消息名称启动流程实例。
     * <p>
     * 校验固定 START 的直接后继是消息名称匹配的 {@code MESSAGE_START} 事件节点，
     * payload 合并为流程变量，从事件出口创建实例；事件节点不成为实例当前位置。
     *
     * @param businessId 业务主键（调用方指定）
     * @param flowCode   流程编码
     * @param eventName  消息名称
     * @param payload    消息载荷，合并为流程变量
     * @param context    触发上下文（handler 为操作人，tenantId 显式指定租户）
     * @return 已持久化的流程实例
     */
    Instance startByMessage(String businessId, String flowCode, String eventName
        , Map<String, Object> payload, WorkflowContext context);

    /**
     * 按实例主键 + 消息名称触发等待中的消息事件。
     * <p>
     * 匹配 {@code MESSAGE_CATCH} / {@code MESSAGE_BOUNDARY} 且消息名称相等的
     * {@code WAITING / RETRY} 等待记录；通过条件更新抢占，记录已是
     * {@code RUNNING / CANCELLED / DEAD} 时不强行覆盖（返回当前实例，流程不变）。
     *
     * @param instanceId 流程实例主键
     * @param eventName  消息名称
     * @param payload    消息载荷，合并为流程变量
     * @param context    触发上下文
     * @return 触发后的流程实例（无匹配或抢占失败时为触发前的实例）
     */
    Instance triggerMessage(Long instanceId, String eventName
        , Map<String, Object> payload, WorkflowContext context);

    /**
     * 实例级抛出流程错误，触发匹配的错误捕获事件。
     * <p>
     * 按 {@code instanceId + 错误码} 匹配 {@code ERROR_CATCH} 等待记录，
     * 精确错误码优先于通配（捕获方未配置错误码表示通配）；匹配成功后按条件更新抢占，
     * 沿错误捕获出口推进流程。实例内无任何匹配时抛出「流程错误未被捕获」异常，
     * 实例、任务与等待记录状态不变，由调用方事务回滚。
     *
     * @param instanceId   流程实例主键
     * @param errorCode    错误码
     * @param errorMessage 错误描述，仅用于日志与异常信息
     * @return 抛出成功返回 {@code true}
     */
    boolean throwError(Long instanceId, String errorCode, String errorMessage);

    /**
     * 根据实例ids，删除流程
     *
     * @param instanceIds: 流程实例集合
     */
    boolean remove(List<Long> instanceIds);

    /**
     * 根据流程定义id，查询流程实例集合
     *
     * @param definitionId 流程定义id
     * @return List<Instance>
     */
    List<Instance> getByDefId(Long definitionId);

    /**
     * 激活实例
     *
     * @param id 流程实例id
     */
    boolean active(Long id);

    /**
     * 挂起实例，流程实例挂起后，该流程实例无法继续流转
     *
     * @param id 流程实例id
     */
    boolean unActive(Long id);

    /**
     * 根据流程定义id集合，查询流程实例集合
     *
     * @param defIds 流程定义id集合
     * @return 流程实例集合
     */
    List<Instance> listByDefIds(List<Long> defIds);


    /**
     * 按照流程变量key删除流程变量
     *
     * @param instanceId 流程实例id
     * @param keys       流程变量key
     */
    void removeVariables(Long instanceId, String... keys);
}

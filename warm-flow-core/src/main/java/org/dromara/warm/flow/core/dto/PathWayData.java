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
package org.dromara.warm.flow.core.dto;


import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Skip;

import java.util.ArrayList;
import java.util.List;


/**
 * 流程办理路径数据。
 * <p>
 * 记录一次通过或退回操作实际经过的节点、连线和最终目标节点，供流程图渲染、
 * 监听器上下文和路径判断使用。
 *
 * @author warm
 * @since 2025/1/4
 */
@Getter
@Setter
@Accessors(chain = true)
public class PathWayData {

    /**
     * 流程定义 ID。
     */
    private Long defId;

    /**
     * 流程实例 ID。
     */
    private Long insId;

    /**
     * 本次流转类型（PASS 审批通过，REJECT 退回）。
     */
    private String skipType;

    /**
     * 流程图元数据合并基底：调用方内存中的最新实例快照。
     * <p>
     * 事件分派发生在实例持久化之前时，库中 defJson 尚未包含本次已写入的节点状态，
     * 直接按实例 ID 回库会拿到旧快照，把已办理节点覆盖回「未完成」；
     * 传入该字段可保证在最新状态上增量合并，为空时退化为按实例 ID 加载。
     */
    private String defJson;

    /**
     * 路径解析后实际到达的目标节点集合。
     */
    private List<Node> targetNodes = new ArrayList<>();

    /**
     * 本次操作经过的节点集合。
     */
    private List<Node> pathWayNodes = new ArrayList<>();

    /**
     * 本次操作经过的节点连线集合。
     */
    private List<Skip> pathWaySkips = new ArrayList<>();

}

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


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Skip;

import java.util.ArrayList;
import java.util.List;


/**
 * 流程定义运行时聚合数据。
 * <p>
 * 将定义、节点和连线集中在一个对象中，供流程路径解析、网关路由和流程执行阶段复用，
 * 避免在一次操作中反复查询同一份流程图。
 *
 * @author warm
 * @since 2023/3/30 14:27
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FlowCombine {

    /**
     * 流程定义主体。
     */
    private Definition definition = FlowEngine.newDef();

    /**
     * 当前流程定义的全部节点。
     */
    private List<Node> allNodes = new ArrayList<>();

    /**
     * 当前流程定义的全部节点跳转连线，按扁平列表保存。
     */
    private List<Skip> allSkips = new ArrayList<>();

}

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

import org.dromara.warm.flow.core.entity.Skip;

import java.io.Serializable;
import java.util.Collection;

/**
 * 节点跳转关联 DAO 接口。
 * <p>
 * 连线记录描述当前节点到目标节点的跳转类型、条件表达式和目标节点类型，
 * 是通过、退回和网关路由的基础数据。
 *
 * @author warm
 * @since 2023-03-29
 */
public interface FlowSkipDao<T extends Skip> extends WarmDao<T> {

    /**
     * 根据流程定义 ID 集合批量删除节点跳转关联。
     *
     * @param defIds 流程定义 ID 集合
     * @return 受影响行数
     */
    public int deleteSkipByDefIds(Collection<? extends Serializable> defIds);
}

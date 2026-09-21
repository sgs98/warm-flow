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
package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Skip;
import org.dromara.warm.flow.core.orm.dao.FlowSkipDao;
import org.dromara.warm.flow.core.orm.service.impl.WarmServiceImpl;
import org.dromara.warm.flow.core.service.SkipService;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * 节点跳转关系服务实现。
 *
 * <p>提供按流程定义和当前节点查询、清理连线的基础能力。</p>
 *
 * @author warm
 * @since 2023-03-29
 */
public class SkipServiceImpl extends WarmServiceImpl<FlowSkipDao<Skip>, Skip> implements SkipService {

    /**
     * 注入流程连线 DAO。
     *
     * @param warmDao 流程连线数据访问对象
     * @return 当前服务实例
     */
    @Override
    public SkipService setDao(FlowSkipDao<Skip> warmDao) {
        this.warmDao = warmDao;
        return this;
    }

    /**
     * 批量删除指定流程定义下的全部连线。
     *
     * @param defIds 流程定义主键集合
     * @return 受影响行数
     */
    @Override
    public int deleteSkipByDefIds(Collection<? extends Serializable> defIds) {
        return getDao().deleteSkipByDefIds(defIds);
    }

    /**
     * 按流程定义主键查询全部连线。
     *
     * @param definitionId 流程定义主键
     * @return 流程定义下的全部连线
     */
    @Override
    public List<Skip> getByDefId(Long definitionId) {
        return list(FlowEngine.newSkip().setDefinitionId(definitionId));
    }

    /**
     * 按流程定义主键和起始节点编码查询出口连线。
     *
     * @param definitionId 流程定义主键
     * @param nodeCode     起始节点编码
     * @return 当前节点的出口连线
     */
    @Override
    public List<Skip> getByDefIdAndNowNodeCode(Long definitionId, String nodeCode) {
        return list(FlowEngine.newSkip().setDefinitionId(definitionId).setNowNodeCode(nodeCode));
    }
}

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

/**
 * 流程操作类型：{@link FlowStatusMachine} 状态前置（guard）表的行键，包内私有。
 *
 * <p>范围裁定：跳转（jump）复用 EXECUTE 行——携带目标节点的办理变体，状态前置无差异，
 * 路径守卫（TAR_NOT_GATEWAY 等）属图形状校验不进状态表；转办/委派/加减签复用
 * UPDATE_HANDLERS 行——协作守卫（IS_ALREADY_* 等）属参数校验，同样不进表。</p>
 *
 * @author warm
 */
enum FlowOp {

    /**
     * 办理（通过/驳回/跳转共用）。
     */
    EXECUTE,

    /**
     * 终止。
     */
    TERMINATE,

    /**
     * 办理人调整（转办/委派/加减签）。
     */
    UPDATE_HANDLERS,

    /**
     * 表单读取。
     */
    LOAD,

    /**
     * 撤回。
     */
    REVOKE,

    /**
     * 按实例删除——guard 表唯一离群行：仅要求激活，终态/已结束实例可删（清理已完成流程是合法场景）。
     */
    DELETE
}

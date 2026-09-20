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

import java.util.List;
import java.util.Map;

/**
 * 流程图节点连线 JSON 传输对象。
 * <p>
 * 描述起止节点、跳转类型、条件、坐标和当前办理状态，并承载前端扩展展示信息。
 *
 * @author warm
 * @since 2023-03-29
 */
@Setter
@Getter
@Accessors(chain = true)
public class SkipJson {

    /**
     * 当前节点编码。
     */
    private String nowNodeCode;

    /**
     * 目标节点编码。
     */
    private String nextNodeCode;

    /**
     * 跳转线显示名称。
     */
    private String skipName;

    /**
     * 跳转类型（PASS 审批通过，REJECT 退回）。
     */
    private String skipType;

    /**
     * 跳转条件表达式。
     */
    private String skipCondition;

    /**
     * 流程图连线坐标。
     */
    private String coordinate;

    /**
     * 连线办理状态（0 未办理，1 待办理，2 已办理）。
     */
    private Integer status;

    /**
     * 业务自定义扩展属性。
     */
    private Map<String, Object> extMap;

    /**
     * 连线提示内容。
     */
    private List<String> promptContent;

    /**
     * 创建人。
     */
    private String createBy;

    /**
     * 最后更新人。
     */
    private String updateBy;

}

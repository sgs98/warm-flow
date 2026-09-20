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
import org.dromara.warm.flow.core.entity.Form;

import java.io.Serializable;

/**
 * 流程相关复合数据传输对象。
 * <p>
 * 用于在流程实例、表单和业务扩展数据之间传递同一请求的组合数据。
 *
 * @author vanlin
 * @since 2024-9-24 11:11
 */
@Getter
@Setter
public class FlowDto implements Serializable {

    /**
     * 关联流程或业务数据的主键。
     */
    private Long id;

    /**
     * 内置表单内容。
     */
    private String formContent;

    /**
     * 表单实体。
     */
    private Form form;

    /**
     * 调用方携带的业务扩展数据。
     */
    private Object data;

}

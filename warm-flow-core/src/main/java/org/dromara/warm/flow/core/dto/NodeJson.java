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
import org.dromara.warm.flow.core.utils.MapUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程图节点 JSON 传输对象。
 * <p>
 * 除节点持久化属性外，还包含流程图展示状态、扩展属性、提示信息和节点出口连线。
 *
 * @author warm
 * @since 2023-03-29
 */
@Setter
@Getter
@Accessors(chain = true)
public class NodeJson {

    /**
     * 节点类型（0 开始，1 中间，2 结束，3 互斥网关，4 并行网关，5 包容网关）。
     */
    private Integer nodeType;

    /**
     * 流程节点编码   每个流程的nodeCode是唯一的,即definitionId+nodeCode唯一,在数据库层面做了控制
     */
    private String nodeCode;

    /**
     * 流程节点名称
     */
    private String nodeName;

    /**
     * 权限标识（权限类型:权限标识，可以多个，用@@隔开)
     */
    private String permissionFlag;

    /**
     * 流程签署比例值
     */
    private String nodeRatio;

    /**
     * 流程节点坐标
     */
    private String coordinate;

    /**
     * 任意结点跳转
     */
    private String anyNodeSkip;

    /**
     * 监听器类型
     */
    private String listenerType;

    /**
     * 监听器路径
     */
    private String listenerPath;

    /**
     * 审批表单是否自定义（Y=是 N=否）
     */
    private String formCustom;

    /**
     * 审批表单是否自定义（Y=是 N=否）
     */
    private String formPath;

    /**
     * 节点扩展属性
     */
    private String ext;

    /**
     * 办理状态: 0未办理 1待办理 2已办理
     */
    private Integer status;

    /**
     * 扩展map，保存业务自定义扩展属性
     */
    private Map<String, Object> extMap;

    /**
     * 流程图节点提示内容
     */
    private PromptContent promptContent;

    /**
     * 跳转条件
     */
    private List<SkipJson> skipList = new ArrayList<>();

    /**
     * 创建人。
     */
    private String createBy;

    /**
     * 最后更新人。
     */
    private String updateBy;

    /**
     * 获取扩展属性；未初始化时返回并缓存一个可写 Map。
     *
     * @return 扩展属性 Map
     */
    public Map<String, Object> getExtMap() {
        if (MapUtil.isEmpty(extMap)) {
            extMap = new HashMap<>();
        }
        return extMap;
    }
}

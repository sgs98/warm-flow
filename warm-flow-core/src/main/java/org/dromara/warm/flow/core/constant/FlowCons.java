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
package org.dromara.warm.flow.core.constant;

import java.util.regex.Pattern;

/**
 * warm-flow 通用常量。
 * <p>
 * 包含表达式分隔符、内置表达式类型、系统变量名、ID 策略名称和表单相关标识。
 *
 * @author warm
 */
public class FlowCons {

    /**
     * 表达式策略类型与表达式主体之间的分隔符。
     */
    public static final String SPLIT_AT = "@@";

    /**
     * 竖线分隔符的正则写法，用于拆分条件表达式片段。
     */
    public static final String SPLIT_VERTICAL = "\\|";

    /**
     * 默认表达式策略类型。
     */
    public static final String DEFAULT = "default";

    /**
     * SpEL 表达式策略类型。
     */
    public static final String SPEL = "spel";

    /**
     * 监听器路径解析正则，拆分监听器类路径与参数片段。
     */
    public static final Pattern LISTENER_PATTERN = Pattern.compile("^([^()]*)(.*)$");

    /**
     * 权限标识中的发起人标识符，办理过程中进行替换
     */
    public static final String WARMFLOWINITIATOR = "warmFlowInitiator";

    /**
     * 监听器参数
     */
    public static final String WARM_LISTENER_PARAM = "WarmListenerParam";

    /**
     * 雪花id 14位
     */
    public static final String SNOWID14 = "SnowId14";

    /**
     * 雪花id 15位
     */
    public static final String SNOWID15 = "SnowId15";

    /**
     * 雪花id 19位
     */
    public static final String SNOWID19 = "SnowId19";


    /**
     * 使用内置表单。
     */
    public static final String FORM_CUSTOM_Y = "Y";

    /**
     * 使用外挂表单路径。
     */
    public static final String FORM_CUSTOM_N = "N";

    /**
     * 表单数据变量名。
     */
    public static final String FORM_DATA = "formData";

    /**
     * 查询或流程图扩展中表示前置节点的 key。
     */
    public static final String PREVIOUS = "previous";

    /**
     * 查询或流程图扩展中表示后缀信息的 key。
     */
    public static final String SUFFIX = "suffix";

}

package org.dromara.warm.demo.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 流程定义列表项 VO。
 *
 * @author may
 * @since 2026/9/5
 */
@Getter
@Setter
public class DefinitionSummaryVo {

    /**
     * 流程定义主键。
     */
    private Long id;

    /**
     * 流程编码。
     */
    private String flowCode;

    /**
     * 流程名称。
     */
    private String flowName;

    /**
     * 设计器模式。
     */
    private String modelValue;

    /**
     * 流程分类编码。
     */
    private String category;

    /**
     * 流程版本。
     */
    private String version;

    /**
     * 是否发布。
     */
    private Integer isPublish;

    /**
     * 流程定义激活状态。
     */
    private Integer activityStatus;

    /**
     * 是否自定义表单。
     */
    private String formCustom;

    /**
     * 表单路径。
     */
    private String formPath;

    /**
     * 监听器类型。
     */
    private String listenerType;

    /**
     * 监听器路径。
     */
    private String listenerPath;

    /**
     * 扩展属性 JSON。
     */
    private String ext;

    /**
     * 创建时间。
     */
    private Date createTime;

    /**
     * 更新时间。
     */
    private Date updateTime;
}

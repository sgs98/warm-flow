package org.dromara.warm.flow.core.test.entity;

import lombok.Data;
import lombok.experimental.Accessors;
import org.dromara.warm.flow.core.entity.Skip;

import java.util.Date;

/**
 * 单测内存实现：节点连线。
 *
 * @author warm
 */
@Data
@Accessors(chain = true)
public class TestSkip implements Skip {
    private Long id;
    private Date createTime;
    private Date updateTime;
    private String createBy;
    private String updateBy;
    private String tenantId;
    private String delFlag;
    private Long definitionId;
    private Long nodeId;
    private String nowNodeCode;
    private Integer nowNodeType;
    private String nextNodeCode;
    private Integer nextNodeType;
    private String skipName;
    private String skipType;
    private String skipCondition;
    private String coordinate;
}

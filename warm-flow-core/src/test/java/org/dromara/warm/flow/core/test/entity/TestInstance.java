package org.dromara.warm.flow.core.test.entity;

import lombok.Data;
import lombok.experimental.Accessors;
import org.dromara.warm.flow.core.entity.Instance;

import java.util.Date;

/**
 * 单测内存实现：流程实例。
 *
 * @author warm
 */
@Data
@Accessors(chain = true)
public class TestInstance implements Instance {
    private Long id;
    private Date createTime;
    private Date updateTime;
    private String createBy;
    private String updateBy;
    private String tenantId;
    private String delFlag;
    private Long definitionId;
    private String flowName;
    private String businessId;
    private Integer nodeType;
    private String nodeCode;
    private String nodeName;
    private String variable;
    private String flowStatus;
    private Integer activityStatus;
    private String formCustom;
    private String formPath;
    private String defJson;
    private String ext;
}

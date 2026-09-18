package org.dromara.warm.flow.core.test.entity;

import lombok.Data;
import lombok.experimental.Accessors;
import org.dromara.warm.flow.core.entity.HisTask;

import java.util.Date;
import java.util.List;

/**
 * 单测内存实现：历史任务。
 *
 * @author warm
 */
@Data
@Accessors(chain = true)
public class TestHisTask implements HisTask {
    private Long id;
    private Date createTime;
    private Date updateTime;
    private String tenantId;
    private String delFlag;
    private Long definitionId;
    private String flowName;
    private Long instanceId;
    private Long taskId;
    private Integer cooperateType;
    private String businessId;
    private String nodeCode;
    private String nodeName;
    private Integer nodeType;
    private String targetNodeCode;
    private String targetNodeName;
    private String approver;
    private String collaborator;
    private List<String> permissionList;
    private String skipType;
    private String flowStatus;
    private String message;
    private String variable;
    private String ext;
    private String formCustom;
    private String formPath;
}

package org.dromara.warm.flow.core.test.entity;

import lombok.Data;
import lombok.experimental.Accessors;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;

import java.util.Date;
import java.util.List;

/**
 * 单测内存实现：待办任务。
 *
 * @author warm
 */
@Data
@Accessors(chain = true)
public class TestTask implements Task {
    private Long id;
    private Date createTime;
    private Date updateTime;
    private String createBy;
    private String updateBy;
    private String tenantId;
    private String delFlag;
    private Long definitionId;
    private Long instanceId;
    private String flowName;
    private String businessId;
    private String nodeCode;
    private String nodeName;
    private Integer nodeType;
    private String flowStatus;
    private List<String> permissionList;
    private List<User> userList;
    private String formCustom;
    private String formPath;
}

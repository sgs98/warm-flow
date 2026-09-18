package org.dromara.warm.flow.core.test.entity;

import lombok.Data;
import lombok.experimental.Accessors;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.User;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 单测内存实现：流程定义。
 *
 * @author warm
 */
@Data
@Accessors(chain = true)
public class TestDefinition implements Definition {
    private Long id;
    private Date createTime;
    private Date updateTime;
    private String createBy;
    private String updateBy;
    private String tenantId;
    private String delFlag;
    private String flowCode;
    private String flowName;
    private String modelValue;
    private String category;
    private String version;
    private Integer isPublish;
    private String formCustom;
    private String formPath;
    private Integer activityStatus;
    private String listenerType;
    private String listenerPath;
    private String ext;
    private List<Node> nodeList = new ArrayList<>();
    private List<User> userList = new ArrayList<>();
}

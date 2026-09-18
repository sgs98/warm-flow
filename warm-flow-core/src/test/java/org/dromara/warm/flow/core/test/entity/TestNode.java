package org.dromara.warm.flow.core.test.entity;

import lombok.Data;
import lombok.experimental.Accessors;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Skip;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 单测内存实现：流程节点。
 *
 * @author warm
 */
@Data
@Accessors(chain = true)
public class TestNode implements Node {
    private Long id;
    private Date createTime;
    private Date updateTime;
    private String createBy;
    private String updateBy;
    private String tenantId;
    private String delFlag;
    private Integer nodeType;
    private Long definitionId;
    private String nodeCode;
    private String nodeName;
    private String permissionFlag;
    private String nodeRatio;
    private String coordinate;
    private String anyNodeSkip;
    private String listenerType;
    private String listenerPath;
    private String formCustom;
    private String formPath;
    private String ext;
    private List<Skip> skipList = new ArrayList<>();
}

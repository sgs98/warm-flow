package org.dromara.warm.flow.core.test.entity;

import lombok.Data;
import lombok.experimental.Accessors;
import org.dromara.warm.flow.core.entity.User;

import java.util.Date;

/**
 * 单测内存实现：任务办理人。
 *
 * @author warm
 */
@Data
@Accessors(chain = true)
public class TestUser implements User {
    private Long id;
    private Date createTime;
    private Date updateTime;
    private String createBy;
    private String updateBy;
    private String tenantId;
    private String delFlag;
    private String type;
    private String processedBy;
    private Long associated;
}

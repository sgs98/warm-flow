package org.dromara.warm.flow.core.test.entity;

import lombok.Data;
import lombok.experimental.Accessors;
import org.dromara.warm.flow.core.entity.EventWait;

import java.util.Date;

/**
 * 单测内存实现：事件等待记录。
 *
 * @author may
 */
@Data
@Accessors(chain = true)
public class TestEventWait implements EventWait {
    private Long id;
    private Date createTime;
    private Date updateTime;
    private String tenantId;
    private String delFlag;
    private Long definitionId;
    private Long instanceId;
    private String nodeCode;
    private String eventType;
    private String eventName;
    private Long attachedTaskId;
    private String triggerType;
    private String triggerInterval;
    private Date nextTriggerAt;
    private String timezone;
    private Integer repeatCount;
    private Integer maxRepeatCount;
    private String status;
    private Integer retryCount;
    private Integer maxRetryCount;
    private Date retryAt;
    private String lockOwner;
    private Date lockUntil;
    private Integer version;
    private String payload;
    private String lastError;
}

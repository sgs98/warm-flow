package org.dromara.warm.flow.orm.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;
import org.dromara.warm.flow.core.entity.EventWait;

import java.util.Date;

/**
 * 事件等待记录对象 flow_event_wait
 *
 * @author may
 */
@Data
@Accessors(chain = true)
@TableName("flow_event_wait")
public class FlowEventWait implements EventWait {

    /**
     * 主键
     */
    @TableId
    private Long id;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /**
     * 删除标记
     */
    @TableLogic(value = "0", delval = "1")
    private String delFlag;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 流程定义ID
     */
    private Long definitionId;

    /**
     * 流程实例ID
     */
    private Long instanceId;

    /**
     * 事件节点编码
     */
    private String nodeCode;

    /**
     * 事件类型
     */
    private String eventType;

    /**
     * 事件名称
     */
    private String eventName;

    /**
     * 边界事件关联的待办任务ID
     */
    private Long attachedTaskId;

    /**
     * 定时触发策略类型
     */
    private String triggerType;

    /**
     * 触发策略原始配置
     */
    private String triggerInterval;

    /**
     * 下次触发时间
     */
    private Date nextTriggerAt;

    /**
     * 定时触发使用的时区
     */
    private String timezone;

    /**
     * 已触发次数
     */
    private Integer repeatCount;

    /**
     * 最大触发次数
     */
    private Integer maxRepeatCount;

    /**
     * 等待状态
     */
    private String status;

    /**
     * 已重试次数
     */
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    private Integer maxRetryCount;

    /**
     * 下次重试时间
     */
    private Date retryAt;

    /**
     * 抢占持有者标识
     */
    private String lockOwner;

    /**
     * 抢占租约到期时间
     */
    private Date lockUntil;

    /**
     * 乐观锁版本号
     */
    private Integer version;

    /**
     * 消息事件接收的业务载荷
     */
    private String payload;

    /**
     * 最近一次失败原因摘要
     */
    private String lastError;

}

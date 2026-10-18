package org.dromara.warm.flow.core.service.event;

/**
 * 消息发布扩展点：{@code MESSAGE_THROW} / {@code MESSAGE_END} 到达时由 core 在事件执行
 * 事务内同步调用，宿主可把它接到 HTTP、消息队列或业务事件总线。
 * <p>
 * 投递失败应抛出异常，由当前事务回滚并进入事件重试；需要事务消息与可靠投递时，
 * 由具体 starter 或业务应用在发布器实现里接入 Outbox。core 不承诺投递成功，
 * 但保证调用点与流程状态更新在同一提交边界内。发布器应按
 * {@code eventWaitId} 或业务幂等键去重，避免至少一次语义下的重复副作用。
 *
 * @author may
 */
public interface EventPublisher {

    /**
     * 发布一条事件消息。
     *
     * @param message 事件消息
     */
    void publish(EventMessage message);
}

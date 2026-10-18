package org.dromara.warm.flow.core.timer;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.config.WarmFlow;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.orm.dao.FlowEventWaitDao;

import java.util.Date;

/**
 * 定时记录抢占器。
 * <p>
 * 封装 CAS 抢占与租约计算：到期记录由扫描器发现后，在这里以
 * {@code id + version + 到期时间} 条件更新抢占为 {@code RUNNING}；
 * 同一记录同一时刻只有一个抢占成功者。
 *
 * @author may
 */
public class TimerClaim {

    /**
     * 默认租约时长（毫秒）：配置缺失时的兜底值。
     */
    private static final long DEFAULT_LEASE_MILLIS = 60_000L;

    /**
     * 事件等待记录 DAO。
     */
    private final FlowEventWaitDao<EventWait> dao;

    public TimerClaim(FlowEventWaitDao<EventWait> dao) {
        this.dao = dao;
    }

    /**
     * 抢占一条到期记录。
     *
     * @param wait 扫描到的等待记录（版本号为抢占前版本）
     * @param now  当前时间
     * @return 抢占成功返回 {@code true}；失败说明已被其它线程或节点抢占
     */
    public boolean claim(EventWait wait, Date now) {
        return dao.claim(wait.getId(), wait.getVersion(), TimerWorkerId.get(), now, lockUntil(now));
    }

    /**
     * 按引擎配置计算租约到期时间。
     */
    public Date lockUntil(Date now) {
        WarmFlow config = FlowEngine.getFlowConfig();
        long leaseMillis = config != null && config.getTimerLeaseSeconds() != null
            ? config.getTimerLeaseSeconds() * 1000L : DEFAULT_LEASE_MILLIS;
        return new Date(now.getTime() + leaseMillis);
    }
}

/*
 *    Copyright 2024-2025, Warm-Flow (290631660@qq.com).
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.dromara.warm.flow.core.config;

import lombok.Getter;
import lombok.Setter;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.enums.ChartStatus;
import org.dromara.warm.flow.core.enums.FrameworkType;
import org.dromara.warm.flow.core.json.JsonConvert;
import org.dromara.warm.flow.core.service.impl.ServiceTaskExecutor;
import org.dromara.warm.flow.core.utils.ServiceLoaderUtil;
import org.dromara.warm.flow.core.timer.TimerEngine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * WarmFlow属性配置文件
 *
 * @author warm
 */
@Getter
@Setter
public class WarmFlow implements Serializable {

    /**
     * 开关
     */
    private boolean enabled = true;

    /**
     * 框架类型: springboot
     */
    private FrameworkType framework;

    /**
     * 启动banner
     */
    private boolean banner = true;

    /**
     * 删除模式开关：true 走逻辑删除（删除时更新 del_flag 为 logic-delete-value）；
     * false 全部表物理删除（MyBatis-Plus 扩展包会绕过自身 @TableLogic 直接 DELETE）。
     * 注意：MP 扩展包 @TableLogic 的查询过滤不受此开关影响。
     */
    private boolean logicDelete = true;

    /**
     * 逻辑删除字段值
     */
    private String logicDeleteValue = "1";

    /**
     * 逻辑未删除字段
     */
    private String logicNotDeleteValue = "0";

    /**
     * 数据填充处理类路径
     */
    private String dataFillHandlerPath;

    /**
     * 租户模式处理类路径
     */
    private String tenantHandlerPath;

    /**
     * 办理人权限处理器类路径
     */
    private String permissionHandlerPath;

    /**
     * 全局监听器类路径
     */
    private String globalListenerPath;

    /**
     * 状态映射器类路径：实例 / 任务 / 协作三维语义 → 宿主编码，未配置时使用内置编码
     */
    private String statusMapperPath;

    /**
     * 数据源类型, mybatis模块对orm进一步的封装, 由于各数据库分页语句存在差异,
     * 当配置此参数时, 以此参数结果为基准, 未配置时, 取DataSource中数据源类型,
     * 兜底为mysql数据库
     */
    private String dataSourceType;

    /**
     * ui开关
     */
    private boolean ui = true;

    /**
     * 如果需要工作流共享业务系统权限，默认Authorization，如果有多个token，用逗号分隔
     */
    private String tokenName = "Authorization";

    /**
     * 公共模型流程状态对应的三原色
     */
    private List<String> chartStatusColor;

    /**
     * 经典模式流程状态对应的三原色
     */
    private List<String> chartStatusColorClassics;

    /**
     * 仿钉钉模式流程状态对应的三原色
     */
    private List<String> chartStatusColorMimic;

    /**
     * 是否显示流程图顶部文字
     */
    private boolean topTextShow = true;

    /**
     * 内置定时器开关：事件能力总开关，默认关闭。
     * 只有显式开启并执行 flow_event_wait 建表脚本后才启动扫描线程，
     * 避免只升级 jar 未建表的宿主周期报错（表缺失时 fail-soft 自动停用）。
     */
    private boolean timerEnabled = false;

    /**
     * 定时 / 消息触发时的系统操作人（实例 / 历史的 createBy 兜底身份）
     */
    private String systemHandler = "system";

    /**
     * 定时器执行线程池大小（不超过 2 个线程）
     */
    private Integer timerPoolSize = 2;

    /**
     * 定时器扫描间隔（毫秒），默认 5 秒
     */
    private Long timerScanInterval = 5000L;

    /**
     * 单轮扫描目标条数
     */
    private Integer timerBatchSize = 100;

    /**
     * 单轮每个状态最多扫描页数，防止大量长期挂起记录占满扫描批次
     */
    private Integer timerMaxScanPages = 10;

    /**
     * 抢占租约时长（秒）
     */
    private Long timerLeaseSeconds = 60L;

    /**
     * 默认最大重试次数，超过后等待记录转为 DEAD
     */
    private Integer timerMaxRetryCount = 3;

    /**
     * 执行器租约续期间隔（毫秒）
     */
    private Long timerLeaseRenewInterval = 20000L;

    public void init() {
        // 设置租户模式
        FlowEngine.initTenantHandler(this.getTenantHandlerPath());

        // 设置数据填充处理类
        FlowEngine.initDataFillHandler(this.getDataFillHandlerPath());

        // 设置办理人权限处理类
        FlowEngine.initPermissionHandler(this.getPermissionHandlerPath());

        // 设置全局监听器
        FlowEngine.initGlobalListener(this.getGlobalListenerPath());

        // 设置状态映射器
        FlowEngine.initStatusMapper(this.getStatusMapperPath());

        // 打印banner图
        printBanner();

        // 初始化流程状态对应的自定义三原色
        ChartStatus.initCustomColor(this.getChartStatusColor(), this.getChartStatusColorClassics(), this.getChartStatusColorMimic());

        // 自动节点执行器：内置服务任务执行器，第三方可在其后追加
        FlowEngine.setAutoNodeExecutors(new ArrayList<>(List.of(new ServiceTaskExecutor())));

        // 通过SPI机制
        spiLoad();

        // 内置定时器：显式开启后才启动扫描线程（默认关闭）
        // 显式传入 this：此时 FlowEngine 尚未注册本配置（starter 在 init() 之后才 setFlowConfig）
        if (isTimerEnabled()) {
            TimerEngine.getInstance().start(this);
        }

    }

    /**
     * 关闭引擎持有的后台资源（内置定时器等），幂等。
     * <p>
     * 应用重载或优雅停机时调用，避免守护线程池泄漏。
     */
    public void shutdown() {
        TimerEngine.getInstance().shutdown();
    }

    public void spiLoad() {
        // 通过SPI机制加载json转换策略实现类
        FlowEngine.jsonConvert = ServiceLoaderUtil.loadFirst(JsonConvert.class);
    }

    private void printBanner() {
        if (this.isBanner()) {
            System.out.println("\n" +
                "   ▄     ▄                             ▄▄▄▄▄▄   ▄                     \n" +
                "   █  █  █  ▄▄▄    ▄ ▄▄  ▄▄▄▄▄         █        █     ▄▄▄  ▄     ▄    \n" +
                "   ▀ █▀█ █ ▀   █   █▀  ▀ █ █ █  ▄▄▄▄▄  █▄▄▄▄▄   █    █▀ ▀█ ▀▄ ▄ ▄▀    \n" +
                "    ██ ██▀ ▄▀▀▀█   █     █ █ █         █        █    █   █  █▄█▄█     \n" +
                "    █   █  ▀▄▄▀█   █     █ █ █         █        █▄▄  ▀█▄█▀   █ █      \n" +
                "\n" +
                "\033[32m   :: Warm-Flow ::     (v" + WarmFlow.class.getPackage()
                .getImplementationVersion() + ")\033[0m\n");
        }
    }

}

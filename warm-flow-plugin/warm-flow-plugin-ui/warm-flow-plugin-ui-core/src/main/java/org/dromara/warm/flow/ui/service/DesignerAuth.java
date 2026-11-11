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
package org.dromara.warm.flow.ui.service;

import org.dromara.warm.flow.core.invoker.FrameInvoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 设计器接口统一鉴权入口。
 * <p>
 * 每次请求解析一次 {@link DesignerAuthHandler}：实现存在时按操作分级裁决，
 * 不存在时拒绝全部数据接口——设计器安全由宿主显式接入，不做静默放行。
 *
 * @author warm
 * @since 2.1.1
 */
public final class DesignerAuth {

    private static final Logger log = LoggerFactory.getLogger(DesignerAuth.class);

    private DesignerAuth() {
    }

    /**
     * 读操作裁决：查询流程定义、表单、字典等。
     *
     * @throws DesignerAuthException 未通过鉴权
     */
    public static void checkRead() {
        check("isAuthorized");
    }

    /**
     * 写操作裁决：保存流程定义、写入表单配置。
     *
     * @throws DesignerAuthException 未通过鉴权
     */
    public static void checkWrite() {
        check("canWrite");
    }

    /**
     * 办理任务裁决：设计器内 execute/handle。
     *
     * @throws DesignerAuthException 未通过鉴权
     */
    public static void checkHandleTask() {
        check("canHandleTask");
    }

    private static void check(String method) {
        DesignerAuthHandler handler = FrameInvoker.getBean(DesignerAuthHandler.class);
        if (handler == null) {
            log.debug("DesignerAuthHandler 未配置，拒绝设计器数据接口访问");
            throw new DesignerAuthException("设计器接口未接入认证（未实现 DesignerAuthHandler），访问被拒绝");
        }
        boolean pass;
        try {
            pass = switch (method) {
                case "canWrite" -> handler.canWrite();
                case "canHandleTask" -> handler.canHandleTask();
                default -> handler.isAuthorized();
            };
        } catch (DesignerAuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("DesignerAuthHandler.{} 执行异常，拒绝访问", method, e);
            pass = false;
        }
        if (!pass) {
            throw new DesignerAuthException("未登录或无权访问设计器接口");
        }
    }
}

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
package org.dromara.warm.flow.core.handler;

import java.util.List;

/**
 * 办理人与权限处理器。
 * <p>
 * 引擎通过该扩展点获取当前用户的权限标识和唯一办理人标识：
 * permissionFlag 表示可办理权限（用户、角色、部门等），用于任务权限校验；
 * handler 表示当前操作者唯一标识（通常是用户 ID），用于入库记录发起人、办理人和审计字段。
 *
 * @author shadow
 */
public interface PermissionHandler {

    /**
     * 获取当前用户权限集合。
     * <p>
     * 权限标识可表示用户、角色、部门等，流程引擎会用该集合匹配节点配置的办理权限。
     *
     * @return 当前用户权限集合
     */
    List<String> permissions();

    /**
     * 获取当前办理人：就是确定唯一用的，如用户id，通常用来入库，记录流程实例创建人，办理人
     * 流程引擎会在执行上下文中使用该标识记录操作者
     *
     * @return 当前办理人
     */
    String getHandler();

    /**
     * 转换办理人权限标识。
     * <p>
     * 设计器中预设的办理人可能是角色、部门等业务标识，业务方可在这里转换为真实用户 ID。
     *
     * @param permissions 原始权限标识
     * @return 转换后的权限标识
     */
    default List<String> convertPermissions(List<String> permissions) {
        return permissions;
    }

}

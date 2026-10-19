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
package org.dromara.warm.flow.core.spi;

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

    /**
     * 解析抄送人：把节点上配置的抄送标识展开成具体用户。
     * <p>
     * 抄送只告知、不参与办理，收件人必须是具体用户——{@code dept:001} 这类标识写进留痕后，
     * 只有权限集合里恰好带该标识的人能看到，等于抄送给“部门”这个字符串本身。业务方在这里把
     * 用户、角色、部门等标识统一展开成真实用户标识；展开不到人的标识直接丢弃，引擎不会落库。
     * <p>
     * 返回值同时是「同一条待办是否抄送过」的去重键：节点上配的 {@code dept:001} 与办理时手选的
     * {@code user:zhangsan} 只要展开成同一个标识，就只留一条抄送记录。因此同一用户在不同调用中
     * 必须返回<b>稳定一致</b>的标识——时而裸名、时而带前缀，会让引擎把一个人当成两个人重复通知。
     * <p>
     * 默认沿用 {@link #convertPermissions(List)}：未实现本方法时，抄送标识按办理人同一套规则转换。
     *
     * @param handlers 节点配置的抄送人标识
     * @return 具体用户标识；空集合表示一个都落不到人
     */
    default List<String> resolveCopyHandlers(List<String> handlers) {
        return convertPermissions(handlers);
    }

}

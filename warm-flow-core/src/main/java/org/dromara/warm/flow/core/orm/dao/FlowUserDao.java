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
package org.dromara.warm.flow.core.orm.dao;

import org.dromara.warm.flow.core.entity.User;

import java.util.List;

/**
 * 流程用户 DAO 接口。
 * <p>
 * 用户表用于保存待办、历史、节点等记录关联的办理人、权限人或已处理人。
 * 通过 associated 字段与不同业务表关联，通过 type 区分用户记录用途。
 *
 * @author xiarg
 * @since 2024/5/10 11:15
 */
public interface FlowUserDao<T extends User> extends WarmDao<T> {

    /**
     * 根据待办任务 ID 集合删除关联用户。
     * <p>
     * 待办完成、终止、撤回或实例清理时，需要同步删除待办任务关联的办理人记录。
     *
     * @param taskIdList 待办任务主键集合
     * @return 受影响行数
     * @author xiarg
     * @since 2024/5/10 11:19
     */
    int deleteByTaskIds(List<Long> taskIdList);

    /**
     * 根据关联 ID 集合和用户类型查询权限人或处理人。
     * <p>
     * associated 可以指向待办任务、实例、历史任务、节点等不同业务记录；
     * types 为空时由具体实现返回该关联下全部类型的用户记录。
     *
     * @param associatedList (待办任务，实例，历史表，节点等)id集合
     * @param types          用户表类型
     * @return 用户记录列表
     */
    List<T> listByAssociatedAndTypes(List<Long> associatedList, String[] types);

    /**
     * 根据关联 ID、已处理人集合和用户类型查询用户记录。
     * <p>
     * 会签、票签等协作场景用该查询判断指定办理人是否已经处理过关联任务。
     *
     * @param associated   待办任务id
     * @param processedBys 办理人id集合
     * @param types        用户表类型
     * @return 用户记录列表
     */
    List<T> listByProcessedBys(Long associated, List<String> processedBys, String[] types);
}

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
package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.orm.dao.FlowUserDao;
import org.dromara.warm.flow.core.orm.service.impl.WarmServiceImpl;
import org.dromara.warm.flow.core.service.UserService;
import org.dromara.warm.flow.core.utils.ArrayUtil;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.StreamUtils;

import java.util.*;

/**
 * 流程办理人服务实现。
 *
 * <p>办理人记录通过关联ID绑定待办任务，并通过类型区分审批、转办、委派等职责。</p>
 *
 * @author xiarg
 * @since 2024/5/10 13:57
 */
public class UserServiceImpl extends WarmServiceImpl<FlowUserDao<User>, User> implements UserService {

    /**
     * 注入流程办理人 DAO。
     *
     * @param warmDao 流程办理人数据访问对象
     * @return 当前服务实例
     */
    @Override
    public UserService setDao(FlowUserDao<User> warmDao) {
        this.warmDao = warmDao;
        return this;
    }

    /**
     * 为一批新待办构造办理人记录，并同步回填各任务的办理人列表。
     *
     * @param addTasks 新建待办任务集合
     * @return 待持久化的办理人记录
     */
    @Override
    public List<User> taskAddUsers(List<Task> addTasks) {
        ArrivalPlan.prepareGroupUsers(addTasks);
        List<User> taskUserList = new ArrayList<>();
        if (CollUtil.isNotEmpty(addTasks)) {
            StreamUtils.toList(addTasks, task -> taskUserList.addAll(taskAddUser(task)));
        }
        return taskUserList;
    }

    /**
     * 根据任务权限集合构造审批类型办理人。
     *
     * @param task 待办任务
     * @return 该任务的办理人记录
     */
    @Override
    public List<User> taskAddUser(Task task) {
        // 批量入口已解析过组签办理人；先返回快照，避免为每个普通任务重复加载定义节点
        if (CollUtil.isNotEmpty(task.getUserList())) {
            return task.getUserList();
        }
        ArrivalPlan.prepareGroupUsers(Collections.singletonList(task));
        if (CollUtil.isNotEmpty(task.getUserList())) {
            return task.getUserList();
        }
        // 遍历权限集合，生成流程节点的权限；seq_no 按表达式书写顺序编号，供顺序会签逐个办理
        List<String> permissionList = distinctPermissions(task.getPermissionList());
        List<User> userList = new ArrayList<>(permissionList.size());
        for (int i = 0; i < permissionList.size(); i++) {
            userList.add(structureUser(task.getId(), permissionList.get(i)
                , UserType.APPROVAL.getKey()).setSeqNo(i));
        }
        task.setUserList(userList);
        return userList;
    }

    /**
     * 按待办任务主键批量删除关联办理人。
     *
     * @param ids 待办任务主键集合
     */
    @Override
    public void deleteByTaskIds(List<Long> ids) {
        getDao().deleteByTaskIds(ids);
    }

    /**
     * 查询关联对象下指定类型的办理人标识；类型为空时查询全部类型。
     *
     * @param associated 关联对象主键
     * @param types      办理人类型
     * @return 办理人标识集合
     */
    @Override
    public List<String> getPermission(Long associated, String... types) {
        if (ArrayUtil.isEmpty(types)) {
            return StreamUtils.toList(list(FlowEngine.newUser().setAssociated(associated)), User::getProcessedBy);
        }
        if (types.length == 1) {
            return StreamUtils.toList(list(FlowEngine.newUser().setAssociated(associated).setType(types[0]))
                , User::getProcessedBy);
        }
        return StreamUtils.toList(getDao().listByAssociatedAndTypes(Collections.singletonList(associated), types)
            , User::getProcessedBy);
    }

    /**
     * 查询单个关联对象下指定类型的办理人记录，并针对零个、一个和多个类型选择对应查询方式。
     *
     * @param associated 关联对象主键
     * @param types      办理人类型
     * @return 办理人记录集合
     */
    @Override
    public List<User> listByAssociatedAndTypes(Long associated, String... types) {
        if (ArrayUtil.isEmpty(types)) {
            return list(FlowEngine.newUser().setAssociated(associated));
        }
        if (types.length == 1) {
            return list(FlowEngine.newUser().setAssociated(associated).setType(types[0]));
        }
        return getDao().listByAssociatedAndTypes(Collections.singletonList(associated), types);
    }

    /**
     * 批量查询多个关联对象下指定类型的办理人记录。
     *
     * @param associateds 关联对象主键集合
     * @param types       办理人类型
     * @return 办理人记录集合
     */
    @Override
    public List<User> getByAssociateds(List<Long> associateds, String... types) {
        if (CollUtil.isNotEmpty(associateds) && associateds.size() == 1) {
            return listByAssociatedAndTypes(associateds.get(0), types);
        }
        return getDao().listByAssociatedAndTypes(associateds, types);
    }

    /**
     * 按关联对象、办理人标识和类型查询办理人记录。
     *
     * @param associated  关联对象主键
     * @param processedBy 办理人标识
     * @param types       办理人类型
     * @return 办理人记录集合
     */
    @Override
    public List<User> listByProcessedBys(Long associated, String processedBy, String... types) {
        if (ArrayUtil.isEmpty(types)) {
            return list(FlowEngine.newUser().setAssociated(associated).setProcessedBy(processedBy));
        }
        if (types.length == 1) {
            return list(FlowEngine.newUser().setAssociated(associated).setProcessedBy(processedBy).setType(types[0]));
        }
        return getDao().listByProcessedBys(associated, Collections.singletonList(processedBy), types);
    }

    /**
     * 按关联对象和办理人标识集合批量查询办理人记录。
     *
     * @param associated   关联对象主键
     * @param processedBys 办理人标识集合
     * @param types        办理人类型
     * @return 办理人记录集合
     */
    @Override
    public List<User> getByProcessedBys(Long associated, List<String> processedBys, String... types) {
        if (CollUtil.isNotEmpty(processedBys) && processedBys.size() == 1) {
            return listByProcessedBys(associated, processedBys.get(0), types);
        }
        return getDao().listByProcessedBys(associated, processedBys, types);
    }


    /**
     * 更新关联对象的办理权限。
     *
     * <p>{@code clear} 为真时先清理当前操作人创建的旧记录，再写入新的权限集合。</p>
     *
     * @param associated  关联对象主键，任务场景下通常为任务ID
     * @param permissions 新办理人标识集合
     * @param type        办理人类型
     * @param clear       是否先清理旧权限
     * @param handler     本次操作人
     * @return 固定返回 {@code true}
     */
    @Override
    public boolean updatePermission(Long associated, List<String> permissions, String type, boolean clear,
                                    String handler) {
        // 判断是否clear，如果是true，则先删除当前关联id用户数据
        if (clear) {
            getDao().delete(FlowEngine.newUser().setAssociated(associated).setCreateBy(handler));
        }
        // 再新增权限人；顺序会签按传入顺序编号。
        // clear=false 时从现有最大顺序号之后追加；存在未编号存量记录时保持未编号（按 id 兜底），避免插队
        List<User> existing = list(FlowEngine.newUser().setAssociated(associated));
        List<String> distinctPermissions = distinctPermissions(permissions);
        List<User> sequenceUsers = existing;
        if (isActiveType(type)) {
            sequenceUsers = StreamUtils.filter(existing, user -> isActiveType(user.getType()));
            Set<String> activeHandlers = new LinkedHashSet<>(StreamUtils.toList(sequenceUsers
                , User::getProcessedBy));
            distinctPermissions.removeIf(activeHandlers::contains);
        }
        Integer startSeqNo = startSeqNo(sequenceUsers);
        List<User> addUsers = new ArrayList<>(distinctPermissions.size());
        for (int i = 0; i < distinctPermissions.size(); i++) {
            User addUser = structureUser(associated, distinctPermissions.get(i), type, handler);
            if (startSeqNo != null) {
                addUser.setSeqNo(startSeqNo + i);
            }
            addUsers.add(addUser);
        }
        saveBatch(addUsers);
        return true;
    }

    /**
     * 计算追加办理人的起始顺序号。
     *
     * @param existing 删除后剩余的办理人记录
     * @return 从删除后剩余记录的最大值 + 1 开始；存在未编号记录或无法确定时返回 {@code null}
     */
    private Integer startSeqNo(List<User> existing) {
        if (CollUtil.isEmpty(existing)) {
            return 0;
        }
        int max = -1;
        for (User user : existing) {
            if (user.getSeqNo() == null) {
                return null;
            }
            if (user.getSeqNo() > max) {
                max = user.getSeqNo();
            }
        }
        return max + 1;
    }

    private List<String> distinctPermissions(List<String> permissions) {
        return CollUtil.isEmpty(permissions)
            ? new ArrayList<>() : new ArrayList<>(new LinkedHashSet<>(permissions));
    }

    private boolean isActiveType(String type) {
        return UserType.APPROVAL.getKey().equals(type)
            || UserType.TRANSFER.getKey().equals(type)
            || UserType.DEPUTE.getKey().equals(type);
    }

    /**
     * 按办理人标识集合批量构造办理人记录。
     *
     * @param associated     关联对象主键
     * @param permissionList 办理人标识集合
     * @param type           办理人类型
     * @return 尚未持久化的办理人记录
     */
    @Override
    public List<User> structureUser(Long associated, List<String> permissionList, String type) {
        return StreamUtils.toList(permissionList, permission -> structureUser(associated, permission, type, null));
    }

    /**
     * 构造单条办理人记录。
     *
     * @param associated 关联对象主键
     * @param permission 办理人标识
     * @param type       办理人类型
     * @return 尚未持久化的办理人记录
     */
    @Override
    public User structureUser(Long associated, String permission, String type) {
        return structureUser(associated, permission, type, null);
    }

    /**
     * 按办理人标识集合和创建人批量构造办理人记录。
     *
     * @param associated     关联对象主键
     * @param permissionList 办理人标识集合
     * @param type           办理人类型
     * @param handler        创建人
     * @return 尚未持久化的办理人记录
     */
    @Override
    public List<User> structureUser(Long associated, List<String> permissionList, String type, String handler) {
        return StreamUtils.toList(permissionList, permission -> structureUser(associated, permission, type, handler));
    }

    /**
     * 构造办理人记录并填充主键，调用方负责后续持久化。
     *
     * @param associated 关联对象主键
     * @param permission 办理人标识
     * @param type       办理人类型
     * @param handler    创建人
     * @return 尚未持久化的办理人记录
     */
    @Override
    public User structureUser(Long associated, String permission, String type, String handler) {
        User user = FlowEngine.newUser()
            .setType(type)
            .setProcessedBy(permission)
            .setAssociated(associated)
            .setCreateBy(handler);
        FlowEngine.dataFillHandler().idFill(user);
        return user;
    }

}

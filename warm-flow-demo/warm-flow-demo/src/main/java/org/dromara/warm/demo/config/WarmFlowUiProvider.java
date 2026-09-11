package org.dromara.warm.demo.config;

import lombok.RequiredArgsConstructor;
import org.dromara.warm.demo.enums.ButtonPermissionEnum;
import org.dromara.warm.demo.service.UserService;
import org.dromara.warm.demo.vo.DemoDepartmentVo;
import org.dromara.warm.demo.vo.DemoUserVo;
import org.dromara.warm.flow.core.dto.Tree;
import org.dromara.warm.flow.ui.service.HandlerDictService;
import org.dromara.warm.flow.ui.service.HandlerSelectService;
import org.dromara.warm.flow.ui.service.ListenerListService;
import org.dromara.warm.flow.ui.service.NodeExtService;
import org.dromara.warm.flow.ui.dto.HandlerQuery;
import org.dromara.warm.flow.ui.vo.Dict;
import org.dromara.warm.flow.ui.vo.HandlerAuth;
import org.dromara.warm.flow.ui.vo.HandlerFeedBackVo;
import org.dromara.warm.flow.ui.vo.HandlerSelectVo;
import org.dromara.warm.flow.ui.vo.ListenerVo;
import org.dromara.warm.flow.ui.vo.NodeExt;
import org.dromara.warm.flow.core.dto.FlowPage;
import org.dromara.warm.flow.ui.utils.TreeUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Demo 对 Warm-Flow iframe 设计器标准扩展接口的适配。
 *
 * @author may
 * @since 2026/9/6
 */
@Component
@RequiredArgsConstructor
public class WarmFlowUiProvider implements NodeExtService, HandlerSelectService,
    HandlerDictService, ListenerListService {

    private final UserService userService;

    @Override
    public List<NodeExt> getNodeExt() {
        NodeExt tab = new NodeExt();
        tab.setCode(ButtonPermissionEnum.PANEL_CODE);
        tab.setName("按钮权限");
        tab.setDesc("节点按钮权限设置");
        tab.setType(2);
        NodeExt.ChildNode child = new NodeExt.ChildNode();
        child.setCode(ButtonPermissionEnum.EXT_CODE);
        child.setLabel("权限按钮");
        child.setType(4);
        child.setMust(false);
        child.setMultiple(true);
        child.setDesc("控制该节点的按钮权限");
        child.setDict(Arrays.stream(ButtonPermissionEnum.values())
            .map(button -> option(button.getLabel(), button.getCode(), button.isDefaultShow()))
            .collect(Collectors.toList()));
        tab.setChilds(Collections.singletonList(child));
        return Collections.singletonList(tab);
    }

    @Override
    public List<String> getHandlerType() {
        return Arrays.asList("用户", "部门");
    }

    @Override
    public HandlerSelectVo getHandlerSelect(HandlerQuery query) {
        boolean departmentType = query != null && "部门".equals(query.getHandlerType());
        List<HandlerAuth> rows = new ArrayList<>();
        Set<String> scope = departmentScope(query == null ? null : query.getGroupId());
        if (departmentType) {
            for (DemoDepartmentVo department : userService.listDepartments()) {
                if (!scope.isEmpty() && !scope.contains(department.getCode())) {
                    continue;
                }
                if (!matches(query, department.getCode(), department.getName())) {
                    continue;
                }
                rows.add(new HandlerAuth()
                    .setStorageId("dept:" + department.getCode())
                    .setHandlerCode(department.getCode())
                    .setHandlerName(department.getName())
                    .setGroupName(parentName(department.getParentCode())));
            }
        } else {
            for (DemoUserVo user : userService.listApprovers()) {
                if (!scope.isEmpty() && !scope.contains(user.getDeptCode())) {
                    continue;
                }
                if (!matches(query, user.getUserName(), user.getRealName())) {
                    continue;
                }
                rows.add(new HandlerAuth()
                    .setStorageId("user:" + user.getUserName())
                    .setHandlerCode(user.getUserName())
                    .setHandlerName(user.getRealName())
                    .setGroupName(user.getDeptName()));
            }
        }
        int pageNum = query == null || query.getPageNum() == null ? 1 : Math.max(query.getPageNum(), 1);
        int pageSize = query == null || query.getPageSize() == null ? 10 : Math.max(query.getPageSize(), 1);
        int from = Math.min((pageNum - 1) * pageSize, rows.size());
        int to = Math.min(from + pageSize, rows.size());
        return new HandlerSelectVo()
            .setHandlerAuths(new FlowPage<HandlerAuth>()
                .setCode(200)
                .setMsg("查询成功")
                .setTotal(rows.size())
                .setRows(rows.subList(from, to)))
            .setTreeSelections(departmentTree());
    }

    @Override
    public List<HandlerFeedBackVo> handlerFeedback(List<String> storageIds) {
        List<HandlerFeedBackVo> result = new ArrayList<>();
        if (storageIds == null || storageIds.isEmpty()) {
            return result;
        }
        Map<String, String> names = new HashMap<>();
        for (DemoUserVo user : userService.listApprovers()) {
            names.put(user.getUserName(), user.getRealName());
            names.put("user:" + user.getUserName(), user.getRealName());
        }
        for (DemoDepartmentVo department : userService.listDepartments()) {
            names.put("dept:" + department.getCode(), department.getName());
        }
        for (String storageId : storageIds) {
            result.add(new HandlerFeedBackVo(storageId, names.get(storageId)));
        }
        return result;
    }

    @Override
    public List<Dict> getHandlerDict() {
        return Arrays.asList(
            new Dict("默认表达式", "${handler}"),
            new Dict("spel表达式", "#{@user.evalVar(#handler)}"),
            new Dict("其他", "")
        );
    }

    @Override
    public List<ListenerVo> listenerList() {
        return new ArrayList<>();
    }

    private NodeExt.DictItem option(String label, String value, boolean selected) {
        return new NodeExt.DictItem(label, value, selected);
    }

    private boolean matches(HandlerQuery query, String code, String name) {
        if (query == null) {
            return true;
        }
        String codeQuery = trimToNull(query.getHandlerCode());
        String nameQuery = trimToNull(query.getHandlerName());
        return (codeQuery == null || code.contains(codeQuery))
            && (nameQuery == null || name.contains(nameQuery));
    }

    private Set<String> departmentScope(String groupId) {
        Set<String> scope = new HashSet<>();
        if (groupId == null || groupId.trim().isEmpty()) {
            return scope;
        }
        List<DemoDepartmentVo> departments = userService.listDepartments();
        collectDepartments(departments, groupId, scope);
        return scope;
    }

    private void collectDepartments(List<DemoDepartmentVo> departments, String parentCode, Set<String> scope) {
        for (DemoDepartmentVo department : departments) {
            if (department.getCode().equals(parentCode) && scope.add(department.getCode())) {
                collectDepartments(departments, department.getCode(), scope);
            } else if (parentCode.equals(department.getParentCode()) && scope.add(department.getCode())) {
                collectDepartments(departments, department.getCode(), scope);
            }
        }
    }

    private String parentName(String parentCode) {
        if (parentCode == null) {
            return "部门";
        }
        return userService.listDepartments().stream()
            .filter(department -> department.getCode().equals(parentCode))
            .map(DemoDepartmentVo::getName)
            .findFirst()
            .orElse("部门");
    }

    private List<Tree> departmentTree() {
        List<Tree> trees = userService.listDepartments().stream()
            .map(department -> new Tree()
                .setId(department.getCode())
                .setName(department.getName())
                .setParentId(department.getParentCode()))
            .collect(Collectors.toList());
        return TreeUtil.buildTree(trees);
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}

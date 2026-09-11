package org.dromara.warm.demo.service.impl;

import org.dromara.warm.demo.service.UserService;
import org.dromara.warm.demo.vo.DemoDepartmentVo;
import org.dromara.warm.demo.vo.DemoUserVo;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * demo 内存用户与部门业务实现。
 *
 * @author may
 * @since 2026/9/11
 */
@Service
public class UserServiceImpl implements UserService {

    private static final List<DemoDepartmentVo> DEPARTMENTS = Collections.unmodifiableList(Arrays.asList(
        department("company", "总公司", null),
        department("tech", "技术部", "company"),
        department("frontend", "前端组", "tech"),
        department("business", "业务部", "company")
    ));

    private static final List<MemoryUser> USERS = Collections.unmodifiableList(Arrays.asList(
        new MemoryUser(1L, "admin", "管理员", "admin", "company"),
        new MemoryUser(2L, "approver1", "审批人一", "approver", "tech"),
        new MemoryUser(3L, "approver2", "审批人二", "approver", "frontend"),
        new MemoryUser(4L, "approver3", "审批人三", "approver", "business")
    ));

    @Override
    public List<DemoUserVo> listApprovers() {
        return USERS.stream().map(this::toVo).collect(Collectors.toList());
    }

    @Override
    public List<DemoDepartmentVo> listDepartments() {
        return DEPARTMENTS.stream().map(department -> {
            DemoDepartmentVo vo = new DemoDepartmentVo();
            vo.setCode(department.getCode());
            vo.setName(department.getName());
            vo.setParentCode(department.getParentCode());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<String> permissionFlags(String userName) {
        Set<String> flags = new LinkedHashSet<>();
        if (userName == null || userName.trim().isEmpty()) {
            return new ArrayList<>(flags);
        }
        flags.add(userName);
        flags.add("user:" + userName);
        MemoryUser user = findUser(userName);
        if (user != null) {
            for (String deptCode : departmentPath(user.getDeptCode())) {
                flags.add("dept:" + deptCode);
            }
        }
        return new ArrayList<>(flags);
    }

    /** {@inheritDoc} */
    @Override
    public List<DemoUserVo> listByPermissionFlags(List<String> permissionFlags) {
        if (permissionFlags == null || permissionFlags.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> flags = permissionFlags.stream()
            .filter(StringUtils::isNotEmpty)
            .map(String::trim)
            .collect(Collectors.toSet());
        if (flags.isEmpty()) {
            return new ArrayList<>();
        }
        return USERS.stream()
            .filter(user -> permissionFlags(user.getUserName()).stream().anyMatch(flags::contains))
            .map(this::toVo)
            .collect(Collectors.toList());
    }

    private MemoryUser findUser(String userName) {
        for (MemoryUser user : USERS) {
            if (user.getUserName().equals(userName)) {
                return user;
            }
        }
        return null;
    }

    private List<String> departmentPath(String deptCode) {
        List<String> path = new ArrayList<>();
        if (deptCode == null) {
            return path;
        }
        Map<String, DemoDepartmentVo> departmentMap = listDepartments().stream()
            .collect(Collectors.toMap(DemoDepartmentVo::getCode, department -> department));
        String current = deptCode;
        while (current != null && !path.contains(current)) {
            path.add(current);
            DemoDepartmentVo department = departmentMap.get(current);
            current = department == null ? null : department.getParentCode();
        }
        return path;
    }

    private DemoUserVo toVo(MemoryUser user) {
        DemoUserVo vo = new DemoUserVo();
        vo.setId(user.getId());
        vo.setUserName(user.getUserName());
        vo.setRealName(user.getRealName());
        vo.setRoleType(user.getRoleType());
        vo.setDeptCode(user.getDeptCode());
        vo.setDeptName(departmentName(user.getDeptCode()));
        return vo;
    }

    private String departmentName(String deptCode) {
        return DEPARTMENTS.stream()
            .filter(department -> department.getCode().equals(deptCode))
            .map(DemoDepartmentVo::getName)
            .findFirst()
            .orElse(null);
    }

    private static DemoDepartmentVo department(String code, String name, String parentCode) {
        DemoDepartmentVo department = new DemoDepartmentVo();
        department.setCode(code);
        department.setName(name);
        department.setParentCode(parentCode);
        return department;
    }

    private static class MemoryUser {
        private final Long id;
        private final String userName;
        private final String realName;
        private final String roleType;
        private final String deptCode;

        private MemoryUser(Long id, String userName, String realName, String roleType, String deptCode) {
            this.id = id;
            this.userName = userName;
            this.realName = realName;
            this.roleType = roleType;
            this.deptCode = deptCode;
        }

        private Long getId() {
            return id;
        }

        private String getUserName() {
            return userName;
        }

        private String getRealName() {
            return realName;
        }

        private String getRoleType() {
            return roleType;
        }

        private String getDeptCode() {
            return deptCode;
        }
    }
}

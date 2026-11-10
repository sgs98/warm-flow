package org.dromara.warm.demo;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.annotations.Select;
import org.dromara.warm.demo.config.DemoPermissionHandler;
import org.dromara.warm.demo.mapper.DemoTaskQueryMapper;
import org.dromara.warm.demo.service.UserService;
import org.dromara.warm.demo.vo.DemoUserVo;
import org.dromara.warm.flow.orm.entity.FlowTask;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Demo 组签成员解析特征测试。
 *
 * @author may
 */
class GroupSignDemoCharacteristicTest {

    @Test
    void groupHandlersExpandUsersAndDepartmentsAndDropUnknownIdentifiers() {
        UserService userService = mock(UserService.class);
        List<String> members = List.of("user:alice", "dept:001", "role:cfo");
        when(userService.listByPermissionFlags(members))
            .thenReturn(List.of(user("alice"), user("bob"), user("alice")));
        DemoPermissionHandler handler = new DemoPermissionHandler(userService);

        List<String> resolved = handler.resolveGroupHandlers(members);

        assertEquals(List.of("alice", "bob"), resolved, "展开为具体用户名并按用户名去重");
    }

    @Test
    void emptyOrUnresolvableGroupMembersReturnEmpty() {
        UserService userService = mock(UserService.class);
        DemoPermissionHandler handler = new DemoPermissionHandler(userService);

        assertTrue(handler.resolveGroupHandlers(List.of()).isEmpty());
        when(userService.listByPermissionFlags(List.of("role:unknown"))).thenReturn(List.of());
        assertTrue(handler.resolveGroupHandlers(List.of("role:unknown")).isEmpty());
    }

    @Test
    void demoGroupSignFixtureIsValidGroupDefinition() throws Exception {
        String json;
        try (var input = getClass().getResourceAsStream("/json/demo_group_sign.json")) {
            assertTrue(input != null, "组签示例 JSON 必须随 Demo 资源打包");
            json = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        JsonNode root = new ObjectMapper().readTree(json);
        assertEquals("demo_group_sign", root.path("flowCode").asText());

        JsonNode groupNode = null;
        for (JsonNode node : root.path("nodeList")) {
            if ("groupSign".equals(node.path("nodeCode").asText())) {
                groupNode = node;
                break;
            }
        }
        assertTrue(groupNode != null, "示例必须包含 groupSign 节点");
        assertEquals("group", groupNode.path("cooperateType").asText());
        assertEquals("0", groupNode.path("nodeRatio").asText());
        assertTrue(groupNode.path("permissionFlag").isNull() || groupNode.path("permissionFlag").isMissingNode(),
            "组签节点不得配置 permission_flag");

        JsonNode groupSign = new ObjectMapper().readTree(groupNode.path("nodeConfig").asText()).path("groupSign");
        assertEquals("parallel", groupSign.path("order").asText());
        assertEquals(2, groupSign.path("groups").size());
        assertEquals("legal", groupSign.path("groups").get(0).path("id").asText());
        assertEquals(2, groupSign.path("groups").get(0).path("members").size());
        assertEquals("finance", groupSign.path("groups").get(1).path("id").asText());

        assertEquals(1, groupNode.path("skipList").size(), "示例只保留组签 → 结束的一条出口");
        assertEquals("end", groupNode.path("skipList").get(0).path("nextNodeCode").asText());
        assertEquals("PASS", groupNode.path("skipList").get(0).path("skipType").asText());
    }

    @Test
    void todoSqlKeepsGroupSequenceVisibilityBranch() throws Exception {
        Select select = DemoTaskQueryMapper.class
            .getMethod("selectTodoPage", Page.class, List.class).getAnnotation(Select.class);
        String sql = String.join(" ", select.value());

        assertTrue(sql.contains("LOWER(n.cooperate_type) = 'group'"), "组签节点必须进入顺序判定");
        assertTrue(sql.contains("JSON_VALID(n.node_config)"), "非法 node_config 不得让待办查询报错");
        assertTrue(sql.contains("LOWER(JSON_UNQUOTE(JSON_EXTRACT(n.node_config, '$.groupSign.order'))) = 'sequence'")
            , "顺序组签按 groupSign.order 过滤");
        assertTrue(sql.contains("LOWER(gn.cooperate_type) = 'group'"),
            "最小 seq_no 的整组可见分支必须只作用于组签节点");
        assertTrue(sql.contains("MIN(gu.seq_no)"), "顺序组签按最小 seq_no 整组可见");
        assertEquals(sql.chars().filter(ch -> ch == '(').count(),
            sql.chars().filter(ch -> ch == ')').count(), "SQL 括号必须配对");
    }

    private DemoUserVo user(String userName) {
        DemoUserVo vo = new DemoUserVo();
        vo.setUserName(userName);
        return vo;
    }
}

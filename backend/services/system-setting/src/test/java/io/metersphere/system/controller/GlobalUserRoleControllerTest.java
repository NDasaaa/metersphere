package io.metersphere.system.controller;

import base.BaseTest;
import io.metersphere.sdk.constants.InternalUserRole;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.constants.UserRoleType;
import io.metersphere.sdk.dto.Permission;
import io.metersphere.sdk.dto.PermissionDefinitionItem;
import io.metersphere.sdk.dto.request.PermissionSettingUpdateRequest;
import io.metersphere.sdk.dto.request.UserRoleUpdateRequest;
import io.metersphere.sdk.service.BaseUserRolePermissionService;
import io.metersphere.sdk.util.BeanUtils;
import io.metersphere.system.domain.UserRole;
import io.metersphere.system.mapper.UserRoleMapper;
import jakarta.annotation.Resource;
import org.apache.commons.collections.CollectionUtils;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.shaded.org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static io.metersphere.sdk.constants.InternalUserRole.ADMIN;
import static io.metersphere.system.controller.result.SystemResultCode.*;
import static io.metersphere.system.service.GlobalUserRoleService.GLOBAL_SCOPE;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GlobalUserRoleControllerTest extends BaseTest {
    @Resource
    private UserRoleMapper userRoleMapper;
    @Resource
    private BaseUserRolePermissionService baseUserRolePermissionService;

    private static final String BASE_PATH = "/user/role/global/";
    private static final String LIST = "list";
    private static final String ADD = "add";
    private static final String UPDATE = "update";
    private static final String DELETE = "delete/{0}";
    private static final String PERMISSION_SETTING = "permission/setting/{0}";
    private static final String PERMISSION_UPDATE = "permission/update";

    // 保存创建的用户组，方便之后的修改和删除测试使用
    private static UserRole addUserRole;
    @Override
    protected String getBasePath() {
        return BASE_PATH;
    }

    @Test
    void list() throws Exception {

        // @@请求成功
        MvcResult mvcResult = this.requestGetWithOk(LIST)
                .andReturn();
        List<UserRole> userRoles = getResultDataArray(mvcResult, UserRole.class);

        // 校验是否是全局用户组
        userRoles.forEach(item -> Assertions.assertTrue(StringUtils.equals(item.getScopeId(), GLOBAL_SCOPE)));

        // 校验是否包含全部的内置用户组
        List<String> userRoleIds = userRoles.stream().map(UserRole::getId).toList();
        List<String> internalUserRoleIds = Arrays.stream(InternalUserRole.values())
                .map(InternalUserRole::getValue)
                .toList();
        Assertions.assertTrue(CollectionUtils.isSubCollection(internalUserRoleIds, userRoleIds));
    }

    @Test
    @Order(0)
    void add() throws Exception {

        // @@请求成功
        UserRoleUpdateRequest request = new UserRoleUpdateRequest();
        request.setName("test");
        request.setType(UserRoleType.SYSTEM.name());
        request.setDescription("test desc");
        MvcResult mvcResult = this.requestPostWithOkAndReturn(ADD, request);
        UserRole resultData = getResultData(mvcResult, UserRole.class);
        UserRole userRole = userRoleMapper.selectByPrimaryKey(resultData.getId());
        // 校验请求成功数据
        Assertions.assertEquals(request.getName(), userRole.getName());
        Assertions.assertEquals(request.getType(), userRole.getType());
        Assertions.assertEquals(request.getDescription(), userRole.getDescription());
        this.addUserRole = userRole;

        // @@重名校验异常
        this.requestPost(ADD, request)
                .andExpect(
                        jsonPath("$.code")
                                .value(GLOBAL_USER_ROLE_EXIST.getCode())
                );
    }

    @Test
    @Order(1)
    void update() throws Exception {

        // @@请求成功
        UserRoleUpdateRequest request = new UserRoleUpdateRequest();
        request.setId(addUserRole.getId());
        request.setName("test update");
        request.setType(UserRoleType.SYSTEM.name());
        request.setDescription("test desc !!!!");
        this.requestPostWithOk(UPDATE, request);
        // 校验请求成功数据
        UserRole userRoleResult = userRoleMapper.selectByPrimaryKey(request.getId());
        Assertions.assertEquals(request.getName(), userRoleResult.getName());
        Assertions.assertEquals(request.getType(), userRoleResult.getType());
        Assertions.assertEquals(request.getDescription(), userRoleResult.getDescription());

        // @@操作非全局用户组异常
        BeanUtils.copyBean(request, getNonGlobalUserRole());
        this.requestPost(UPDATE, request)
                .andExpect(jsonPath("$.code").value(GLOBAL_USER_ROLE_PERMISSION.getCode()));

        // @@操作内置用户组异常
        request.setId(ADMIN.getValue());
        request.setName(ADMIN.getValue());
        this.requestPost(UPDATE, request)
                .andExpect(jsonPath("$.code").value(INTERNAL_USER_ROLE_PERMISSION.getCode()));

        // @@重名校验异常
        request.setId(addUserRole.getId());
        request.setName("系统管理员");
        this.requestPost(UPDATE, request)
                .andExpect(jsonPath("$.code").value(GLOBAL_USER_ROLE_EXIST.getCode()));
        this.requestPost(UPDATE, new UserRole());
    }

    @Test
    void getPermissionSetting() throws Exception {
        // @@请求成功
        MvcResult mvcResult = this.requestGetWithOkAndReturn(PERMISSION_SETTING, ADMIN.getValue());
        List<PermissionDefinitionItem> permissionDefinition = getResultDataArray(mvcResult, PermissionDefinitionItem.class);
        // 获取该用户组拥有的权限
        Set<String> permissionIds = baseUserRolePermissionService.getPermissionIdSetByRoleId(ADMIN.getValue());
        // 设置勾选项
        permissionDefinition.forEach(firstLevel -> {
            List<PermissionDefinitionItem> children = firstLevel.getChildren();
            boolean allCheck = true;
            for (PermissionDefinitionItem secondLevel : children) {
                List<Permission> permissions = secondLevel.getPermissions();
                if (CollectionUtils.isEmpty(permissions)) {
                    continue;
                }
                boolean secondAllCheck = true;
                for (Permission p : permissions) {
                    if (permissionIds.contains(p.getId())) {
                        // 如果有权限这里校验开启
                        Assertions.assertTrue(p.getEnable());
                        // 使用完移除
                        permissionIds.remove(p.getId());
                    } else {
                        // 如果没有权限校验关闭
                        Assertions.assertFalse(p.getEnable());
                        secondAllCheck = false;
                    }
                }
                // 校验二级菜单启用设置
                Assertions.assertEquals(secondLevel.getEnable(), secondAllCheck);
                if (!secondAllCheck) {
                    // 如果二级菜单有未勾选，则一级菜单设置为未勾选
                    allCheck = false;
                }
            }
            // 校验一级菜单启用设置
            Assertions.assertEquals(firstLevel.getEnable(), allCheck);
        });
        // 校验是不是获取的数据中包含了该用户组所有的权限
        Assertions.assertTrue(CollectionUtils.isEmpty(permissionIds));

        // @@操作非全局用户组异常
        this.requestGet(PERMISSION_SETTING, getNonGlobalUserRole().getId())
                .andExpect(jsonPath("$.code").value(GLOBAL_USER_ROLE_PERMISSION.getCode()));

    }

    @Test
    @Order(2)
    void updatePermissionSetting() throws Exception {

        PermissionSettingUpdateRequest request = new PermissionSettingUpdateRequest();
        request.setPermissions(new ArrayList<>() {{
            PermissionSettingUpdateRequest.PermissionUpdateRequest permission1
                    = new PermissionSettingUpdateRequest.PermissionUpdateRequest();
            permission1.setEnable(true);
            permission1.setId(PermissionConstants.SYSTEM_USER_READ);
            add(permission1);
            PermissionSettingUpdateRequest.PermissionUpdateRequest permission2
                    = new PermissionSettingUpdateRequest.PermissionUpdateRequest();
            permission2.setEnable(false);
            permission2.setId(PermissionConstants.SYSTEM_USER_ROLE_RELATION_READ);
            add(permission2);
        }});

        // @@请求成功
        request.setUserRoleId(addUserRole.getId());
        this.requestPostWithOk(PERMISSION_UPDATE, request);
        // 获取该用户组拥有的权限
        Set<String> permissionIds = baseUserRolePermissionService.getPermissionIdSetByRoleId(request.getUserRoleId());
        Set<String> requestPermissionIds = request.getPermissions().stream()
                .filter(PermissionSettingUpdateRequest.PermissionUpdateRequest::getEnable)
                .map(PermissionSettingUpdateRequest.PermissionUpdateRequest::getId)
                .collect(Collectors.toSet());
        // 校验请求成功数据
        Assertions.assertEquals(requestPermissionIds, permissionIds);

        // @@操作非全局用户组异常
        request.setUserRoleId(getNonGlobalUserRole().getId());
        this.requestPost(PERMISSION_UPDATE, request)
                .andExpect(jsonPath("$.code").value(GLOBAL_USER_ROLE_PERMISSION.getCode()));

        // @@操作内置用户组异常
        request.setUserRoleId(ADMIN.getValue());
        this.requestPost(PERMISSION_UPDATE, request)
                .andExpect(jsonPath("$.code").value(INTERNAL_USER_ROLE_PERMISSION.getCode()));
    }

    @Test
    @Order(3)
    void delete() throws Exception {
        // @@请求成功
        this.requestGet(DELETE, addUserRole.getId());
        // 校验请求成功数据
        Assertions.assertNull(userRoleMapper.selectByPrimaryKey(addUserRole.getId()));

        // @@操作非全局用户组异常
        this.requestGet(DELETE, getNonGlobalUserRole().getId())
                .andExpect(jsonPath("$.code").value(GLOBAL_USER_ROLE_PERMISSION.getCode()));

        // @@操作内置用户组异常
        this.requestGet(DELETE, ADMIN.getValue())
                .andExpect(jsonPath("$.code").value(INTERNAL_USER_ROLE_PERMISSION.getCode()));

    }

    /**
     * 插入一条非全局用户组，并返回
     */
    private UserRole getNonGlobalUserRole() {
        // 插入一条非全局用户组数据
        UserRole nonGlobalUserRole = userRoleMapper.selectByPrimaryKey(ADMIN.getValue());
        nonGlobalUserRole.setName("非全局用户组");
        nonGlobalUserRole.setScopeId("not global");
        nonGlobalUserRole.setId(UUID.randomUUID().toString());
        userRoleMapper.insert(nonGlobalUserRole);
        return nonGlobalUserRole;
    }
}
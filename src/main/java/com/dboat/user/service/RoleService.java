package com.dboat.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dboat.user.entity.Role;

import java.util.List;

/**
* @author tanghj
* @description 针对表【role(RBAC角色表（租户级角色）)】的数据库操作Service
* @createDate 2026-09-13 17:51:34
*/
public interface RoleService extends IService<Role> {

    /**
     * 查询用户在租户内拥有的全部启用角色
     *
     * @param orgId  租户ID
     * @param userId 用户ID
     * @return 角色列表，无角色时返回空列表
     */
    List<Role> listRolesByUserId(String orgId, String userId);

    /**
     * 查询用户在租户内拥有的全部启用角色编码
     *
     * @param orgId  租户ID
     * @param userId 用户ID
     * @return 角色编码列表（如 ADMIN / OPERATOR / VIEWER）
     */
    List<String> listRoleCodesByUserId(String orgId, String userId);

    /**
     * 按租户与角色编码查询启用角色
     *
     * @param orgId    租户ID
     * @param roleCode 角色编码
     * @return 角色实体，不存在时返回 null
     */
    Role getByOrgIdAndRoleCode(String orgId, String roleCode);
}

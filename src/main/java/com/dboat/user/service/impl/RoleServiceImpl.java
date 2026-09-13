package com.dboat.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.user.entity.Role;
import com.dboat.user.enums.CommonStatusEnum;
import com.dboat.user.mapper.RoleMapper;
import com.dboat.user.service.RoleService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
* @author tanghj
* @description 针对表【role(RBAC角色表（租户级角色）)】的数据库操作Service实现
* @createDate 2026-09-13 17:51:34
*/
@Service
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role>
    implements RoleService{

    /**
     * 查询用户在租户内拥有的全部启用角色
     *
     * @param orgId  租户ID
     * @param userId 用户ID
     * @return 角色列表
     */
    @Override
    public List<Role> listRolesByUserId(String orgId, String userId) {
        return baseMapper.selectRolesByUserId(orgId, userId);
    }

    /**
     * 查询用户在租户内拥有的全部启用角色编码
     *
     * @param orgId  租户ID
     * @param userId 用户ID
     * @return 角色编码列表
     */
    @Override
    public List<String> listRoleCodesByUserId(String orgId, String userId) {
        return listRolesByUserId(orgId, userId).stream()
                .map(Role::getRoleCode)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 按租户与角色编码查询启用角色
     *
     * @param orgId    租户ID
     * @param roleCode 角色编码
     * @return 角色实体，不存在时返回 null
     */
    @Override
    public Role getByOrgIdAndRoleCode(String orgId, String roleCode) {
        return getOne(Wrappers.<Role>lambdaQuery()
                .eq(Role::getOrgId, orgId)
                .eq(Role::getRoleCode, roleCode)
                .eq(Role::getStatus, CommonStatusEnum.ENABLED.getCode())
                .isNull(Role::getDeleteTime)
                .last("LIMIT 1"));
    }
}

package com.dboat.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dboat.user.entity.Permission;

import java.util.Collection;
import java.util.List;

/**
* @author tanghj
* @description 针对表【permission(RBAC权限资源表（全局权限定义）)】的数据库操作Service
* @createDate 2026-09-13 17:51:45
*/
public interface PermissionService extends IService<Permission> {

    /**
     * 查询用户经 RBAC 授权后拥有的全部启用权限编码
     *
     * @param orgId  租户ID
     * @param userId 用户ID
     * @return 权限编码列表（如 iot:device:read），无权限时返回空列表
     */
    List<String> listPermCodesByUserId(String orgId, String userId);

    /**
     * 查询系统内全部启用权限编码
     * <p>用于 ADMIN 超级管理员角色直接授予全量权限，避免逐条维护 role_perm。</p>
     *
     * @return 权限编码列表
     */
    List<String> listAllEnabledPermCodes();

    /**
     * 按权限编码批量查询启用权限
     * <p>新租户初始化内置角色权限时，需将权限编码转换为 {@code perm_id} 再写入关联表。</p>
     *
     * @param permCodes 权限编码集合
     * @return 权限实体列表，入参为空时返回空列表
     */
    List<Permission> listByPermCodes(Collection<String> permCodes);
}

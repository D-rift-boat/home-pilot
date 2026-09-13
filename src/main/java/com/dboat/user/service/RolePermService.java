package com.dboat.user.service;

import com.dboat.user.entity.RolePerm;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Collection;

/**
* @author tanghj
* @description 针对表【role_perm(角色权限关联表)】的数据库操作Service
* @createDate 2026-09-13 17:52:37
*/
public interface RolePermService extends IService<RolePerm> {

    /**
     * 按权限编码为角色授权（幂等）
     * <p>
     * 先将权限编码解析为 {@code perm_id}，再过滤掉已存在的关联后批量写入，
     * 因此可重复执行而不产生脏数据。新租户初始化内置角色权限时使用。
     * </p>
     *
     * @param orgId     租户ID
     * @param roleId    角色ID
     * @param permCodes 待授予的权限编码集合
     * @return 实际新增的关联条数
     */
    int grantPermCodes(String orgId, String roleId, Collection<String> permCodes);
}

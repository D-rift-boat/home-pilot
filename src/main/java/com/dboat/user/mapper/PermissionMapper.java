package com.dboat.user.mapper;

import com.dboat.user.entity.Permission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* @author tanghj
* @description 针对表【permission(RBAC权限资源表（全局权限定义）)】的数据库操作Mapper
* @createDate 2026-09-13 17:51:45
* @Entity com.dboat.user.entity.Permission
*/
public interface PermissionMapper extends BaseMapper<Permission> {

    /**
     * 查询指定用户经 RBAC 授权后拥有的全部启用权限编码
     * <p>关联链路：user_role → role_perm → permission，自动过滤软删除与禁用权限。</p>
     *
     * @param orgId  租户ID
     * @param userId 用户ID
     * @return 权限编码列表（已去重）
     */
    List<String> selectPermCodesByUserId(@Param("orgId") String orgId, @Param("userId") String userId);
}





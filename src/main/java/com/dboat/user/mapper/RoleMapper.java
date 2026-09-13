package com.dboat.user.mapper;

import com.dboat.user.entity.Role;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* @author tanghj
* @description 针对表【role(RBAC角色表（租户级角色）)】的数据库操作Mapper
* @createDate 2026-09-13 17:51:34
* @Entity com.dboat.user.entity.Role
*/
public interface RoleMapper extends BaseMapper<Role> {

    /**
     * 查询指定用户在租户内拥有的全部启用角色
     * <p>关联 user_role，自动过滤软删除记录与禁用角色。</p>
     *
     * @param orgId  租户ID
     * @param userId 用户ID
     * @return 角色列表
     */
    List<Role> selectRolesByUserId(@Param("orgId") String orgId, @Param("userId") String userId);
}





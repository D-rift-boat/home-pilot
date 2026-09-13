package com.dboat.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.user.entity.Permission;
import com.dboat.user.entity.RolePerm;
import com.dboat.user.mapper.RolePermMapper;
import com.dboat.user.service.PermissionService;
import com.dboat.user.service.RolePermService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
* @author tanghj
* @description 针对表【role_perm(角色权限关联表)】的数据库操作Service实现
* @createDate 2026-09-13 17:52:36
*/
@Slf4j
@Service
public class RolePermServiceImpl extends ServiceImpl<RolePermMapper, RolePerm>
    implements RolePermService{

    /** 权限资源服务，用于将权限编码解析为 perm_id */
    @Resource
    private PermissionService permissionService;

    /**
     * 按权限编码为角色授权（幂等）
     *
     * @param orgId     租户ID
     * @param roleId    角色ID
     * @param permCodes 待授予的权限编码集合
     * @return 实际新增的关联条数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int grantPermCodes(String orgId, String roleId, Collection<String> permCodes) {
        if (!StringUtils.hasText(orgId) || !StringUtils.hasText(roleId) || CollectionUtils.isEmpty(permCodes)) {
            return 0;
        }
        List<Permission> permissions = permissionService.listByPermCodes(permCodes);
        if (permissions.isEmpty()) {
            // permission 是全局表，缺失说明 RBAC 种子 SQL 尚未执行
            log.warn("【角色授权】权限编码未在 permission 表中找到，请先执行 RBAC 种子数据 SQL orgId={}, roleId={}, permCodes={}",
                    orgId, roleId, permCodes);
            return 0;
        }

        // 查询已存在的关联时不过滤 delete_time：软删除记录仍占用 uk_org_role_perm 唯一键，重复插入会直接报错
        Set<String> existingPermIds = list(Wrappers.<RolePerm>lambdaQuery()
                .select(RolePerm::getPermId)
                .eq(RolePerm::getOrgId, orgId)
                .eq(RolePerm::getRoleId, roleId))
                .stream()
                .map(RolePerm::getPermId)
                .collect(Collectors.toSet());

        LocalDateTime now = LocalDateTime.now();
        List<RolePerm> toSave = new ArrayList<>();
        for (Permission permission : permissions) {
            if (existingPermIds.contains(permission.getPermId())) {
                continue;
            }
            RolePerm rolePerm = new RolePerm();
            rolePerm.setId(UUID.randomUUID().toString().replace("-", ""));
            rolePerm.setOrgId(orgId);
            rolePerm.setRoleId(roleId);
            rolePerm.setPermId(permission.getPermId());
            rolePerm.setCreateTime(now);
            toSave.add(rolePerm);
        }
        if (toSave.isEmpty()) {
            return 0;
        }
        saveBatch(toSave);
        log.info("【角色授权】orgId={}, roleId={}, 新增权限关联 {} 条", orgId, roleId, toSave.size());
        return toSave.size();
    }
}

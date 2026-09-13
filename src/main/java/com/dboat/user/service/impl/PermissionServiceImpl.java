package com.dboat.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.user.entity.Permission;
import com.dboat.user.enums.CommonStatusEnum;
import com.dboat.user.mapper.PermissionMapper;
import com.dboat.user.service.PermissionService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
* @author tanghj
* @description 针对表【permission(RBAC权限资源表（全局权限定义）)】的数据库操作Service实现
* @createDate 2026-09-13 17:51:45
*/
@Service
public class PermissionServiceImpl extends ServiceImpl<PermissionMapper, Permission>
    implements PermissionService{

    /**
     * 查询用户经 RBAC 授权后拥有的全部启用权限编码
     *
     * @param orgId  租户ID
     * @param userId 用户ID
     * @return 权限编码列表
     */
    @Override
    public List<String> listPermCodesByUserId(String orgId, String userId) {
        return baseMapper.selectPermCodesByUserId(orgId, userId);
    }

    /**
     * 查询系统内全部启用权限编码
     *
     * @return 权限编码列表
     */
    @Override
    public List<String> listAllEnabledPermCodes() {
        return list(Wrappers.<Permission>lambdaQuery()
                .select(Permission::getPermCode)
                .eq(Permission::getStatus, CommonStatusEnum.ENABLED.getCode())
                .isNull(Permission::getDeleteTime)
                .orderByAsc(Permission::getSortNum))
                .stream()
                .map(Permission::getPermCode)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 按权限编码批量查询启用权限
     *
     * @param permCodes 权限编码集合
     * @return 权限实体列表
     */
    @Override
    public List<Permission> listByPermCodes(Collection<String> permCodes) {
        if (CollectionUtils.isEmpty(permCodes)) {
            return Collections.emptyList();
        }
        return list(Wrappers.<Permission>lambdaQuery()
                .in(Permission::getPermCode, permCodes)
                .eq(Permission::getStatus, CommonStatusEnum.ENABLED.getCode())
                .isNull(Permission::getDeleteTime));
    }
}

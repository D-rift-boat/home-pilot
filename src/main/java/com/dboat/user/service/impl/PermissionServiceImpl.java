package com.dboat.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.user.entity.Permission;
import com.dboat.user.service.PermissionService;
import com.dboat.user.mapper.PermissionMapper;
import org.springframework.stereotype.Service;

/**
* @author tanghj
* @description 针对表【permission(RBAC权限资源表（全局权限定义）)】的数据库操作Service实现
* @createDate 2026-09-13 17:51:45
*/
@Service
public class PermissionServiceImpl extends ServiceImpl<PermissionMapper, Permission>
    implements PermissionService{

}





package com.dboat.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.user.entity.Role;
import com.dboat.user.service.RoleService;
import com.dboat.user.mapper.RoleMapper;
import org.springframework.stereotype.Service;

/**
* @author tanghj
* @description 针对表【role(RBAC角色表（租户级角色）)】的数据库操作Service实现
* @createDate 2026-09-13 17:51:34
*/
@Service
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role>
    implements RoleService{

}





package com.dboat.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.user.entity.RolePerm;
import com.dboat.user.service.RolePermService;
import com.dboat.user.mapper.RolePermMapper;
import org.springframework.stereotype.Service;

/**
* @author tanghj
* @description 针对表【role_perm(角色权限关联表)】的数据库操作Service实现
* @createDate 2026-09-13 17:52:36
*/
@Service
public class RolePermServiceImpl extends ServiceImpl<RolePermMapper, RolePerm>
    implements RolePermService{

}





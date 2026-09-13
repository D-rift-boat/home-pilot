package com.dboat.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.user.entity.UserRole;
import com.dboat.user.service.UserRoleService;
import com.dboat.user.mapper.UserRoleMapper;
import org.springframework.stereotype.Service;

/**
* @author tanghj
* @description 针对表【user_role(用户角色关联表)】的数据库操作Service实现
* @createDate 2026-09-13 17:53:39
*/
@Service
public class UserRoleServiceImpl extends ServiceImpl<UserRoleMapper, UserRole>
    implements UserRoleService{

}





package com.dboat.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.user.entity.User;
import com.dboat.user.service.UserService;
import com.dboat.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
* @author tanghj
* @description 针对表【user(系统用户主表（RBAC主体）)】的数据库操作Service实现
* @createDate 2026-09-13 17:49:51
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

}





package com.dboat.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.user.entity.UserAuth;
import com.dboat.user.service.UserAuthService;
import com.dboat.user.mapper.UserAuthMapper;
import org.springframework.stereotype.Service;

/**
* @author tanghj
* @description 针对表【user_auth(用户认证凭据表（登录凭据与用户业务解耦）)】的数据库操作Service实现
* @createDate 2026-09-13 17:53:17
*/
@Service
public class UserAuthServiceImpl extends ServiceImpl<UserAuthMapper, UserAuth>
    implements UserAuthService{

}





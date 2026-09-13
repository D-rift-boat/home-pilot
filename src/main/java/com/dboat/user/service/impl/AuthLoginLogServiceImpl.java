package com.dboat.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.user.entity.AuthLoginLog;
import com.dboat.user.service.AuthLoginLogService;
import com.dboat.user.mapper.AuthLoginLogMapper;
import org.springframework.stereotype.Service;

/**
* @author tanghj
* @description 针对表【auth_login_log(登录审计日志表)】的数据库操作Service实现
* @createDate 2026-09-13 18:29:59
*/
@Service
public class AuthLoginLogServiceImpl extends ServiceImpl<AuthLoginLogMapper, AuthLoginLog>
    implements AuthLoginLogService{

}





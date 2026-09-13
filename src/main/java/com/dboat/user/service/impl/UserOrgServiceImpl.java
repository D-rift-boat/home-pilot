package com.dboat.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.user.entity.UserOrg;
import com.dboat.user.mapper.UserOrgMapper;
import com.dboat.user.service.UserOrgService;
import org.springframework.stereotype.Service;

/**
* @author tanghj
* @description 针对表【org(租户组织表)】的数据库操作Service实现
* @createDate 2026-09-13 17:51:59
*/
@Service
public class UserOrgServiceImpl extends ServiceImpl<UserOrgMapper, UserOrg>
    implements UserOrgService {

}





package com.dboat.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.user.common.constants.AuthConstants;
import com.dboat.user.entity.UserOrg;
import com.dboat.user.enums.CommonStatusEnum;
import com.dboat.user.mapper.UserOrgMapper;
import com.dboat.user.service.UserOrgService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
* @author tanghj
* @description 针对表【org(租户组织表)】的数据库操作Service实现
* @createDate 2026-09-13 17:51:59
*/
@Service
public class UserOrgServiceImpl extends ServiceImpl<UserOrgMapper, UserOrg>
    implements UserOrgService{

    /**
     * 查询启用状态的租户
     *
     * @param orgId 租户ID
     * @return 租户实体，不存在或已禁用时返回 null
     */
    @Override
    public UserOrg getEnabledOrg(String orgId) {
        if (!StringUtils.hasText(orgId)) {
            return null;
        }
        return getOne(Wrappers.<UserOrg>lambdaQuery()
                .eq(UserOrg::getOrgId, orgId)
                .eq(UserOrg::getStatus, CommonStatusEnum.ENABLED.getCode())
                .isNull(UserOrg::getDeleteTime)
                .last("LIMIT 1"));
    }

    /**
     * 创建租户
     *
     * @param orgId   租户ID，为空时自动生成 UUID（去横线）
     * @param orgName 租户名称，为空时使用默认名称
     * @return 创建成功的租户实体
     */
    @Override
    public UserOrg createOrg(String orgId, String orgName) {
        UserOrg org = new UserOrg();
        org.setOrgId(StringUtils.hasText(orgId) ? orgId : UUID.randomUUID().toString().replace("-", ""));
        org.setOrgName(StringUtils.hasText(orgName) ? orgName : AuthConstants.DEFAULT_ORG_NAME);
        org.setStatus(CommonStatusEnum.ENABLED.getCode());
        org.setCreateTime(LocalDateTime.now());
        org.setUpdateTime(LocalDateTime.now());
        save(org);
        return org;
    }
}

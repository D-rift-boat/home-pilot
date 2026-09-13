package com.dboat.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dboat.user.entity.UserOrg;

/**
* @author tanghj
* @description 针对表【org(租户组织表)】的数据库操作Service
* @createDate 2026-09-13 17:51:59
*/
public interface UserOrgService extends IService<UserOrg> {

    /**
     * 查询启用状态的租户
     *
     * @param orgId 租户ID
     * @return 租户实体，不存在或已禁用时返回 null
     */
    UserOrg getEnabledOrg(String orgId);

    /**
     * 创建租户
     * <p>orgId 为空时自动生成 UUID，创建后自动填充 create_time。</p>
     *
     * @param orgId   租户ID，可为空
     * @param orgName 租户名称
     * @return 创建成功的租户实体（含最终 orgId）
     */
    UserOrg createOrg(String orgId, String orgName);
}

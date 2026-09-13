package com.dboat.iot.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.iot.entity.IotGroupUserRel;
import com.dboat.iot.service.IotGroupUserRelService;
import com.dboat.iot.mapper.IotGroupUserRelMapper;
import org.springframework.stereotype.Service;

/**
* @author tanghj
* @description 针对表【iot_group_user_rel(分组-用户业务权限关联表)】的数据库操作Service实现
* @createDate 2026-09-13 18:12:41
*/
@Service
public class IotGroupUserRelServiceImpl extends ServiceImpl<IotGroupUserRelMapper, IotGroupUserRel>
    implements IotGroupUserRelService{

}





package com.dboat.iot.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.iot.entity.IotDevGroupRel;
import com.dboat.iot.service.IotDevGroupRelService;
import com.dboat.iot.mapper.IotDevGroupRelMapper;
import org.springframework.stereotype.Service;

/**
* @author tanghj
* @description 针对表【iot_dev_group_rel(设备和分组关联表（多对多）)】的数据库操作Service实现
* @createDate 2026-09-13 18:12:41
*/
@Service
public class IotDevGroupRelServiceImpl extends ServiceImpl<IotDevGroupRelMapper, IotDevGroupRel>
    implements IotDevGroupRelService{

}





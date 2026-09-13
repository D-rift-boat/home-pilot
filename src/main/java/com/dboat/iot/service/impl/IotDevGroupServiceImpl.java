package com.dboat.iot.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.iot.entity.IotDevGroup;
import com.dboat.iot.service.IotDevGroupService;
import com.dboat.iot.mapper.IotDevGroupMapper;
import org.springframework.stereotype.Service;

/**
* @author tanghj
* @description 针对表【iot_dev_group(IoT设备分组表（树形）)】的数据库操作Service实现
* @createDate 2026-09-13 18:12:41
*/
@Service
public class IotDevGroupServiceImpl extends ServiceImpl<IotDevGroupMapper, IotDevGroup>
    implements IotDevGroupService{

}





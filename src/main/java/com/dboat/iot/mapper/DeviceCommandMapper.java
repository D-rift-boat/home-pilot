package com.dboat.iot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dboat.iot.entity.DeviceCommand;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceCommandMapper extends BaseMapper<DeviceCommand> {
}

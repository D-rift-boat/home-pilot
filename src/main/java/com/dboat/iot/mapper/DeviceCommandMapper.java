package com.dboat.iot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dboat.iot.entity.DeviceCommand;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备指令 Mapper 接口
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，自动获得 DeviceCommand 实体的 CRUD 操作能力。
 * 如需自定义 SQL，可在 resources/mapper/DeviceCommandMapper.xml 中编写。
 * </p>
 *
 * @author dboat
 */
@Mapper
public interface DeviceCommandMapper extends BaseMapper<DeviceCommand> {
}

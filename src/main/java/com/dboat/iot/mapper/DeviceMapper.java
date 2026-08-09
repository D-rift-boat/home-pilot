package com.dboat.iot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dboat.iot.entity.Device;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备资产 Mapper 接口
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，自动获得 Device 实体的 CRUD 操作能力。
 * 如需自定义 SQL，可在 resources/mapper/DeviceMapper.xml 中编写。
 * </p>
 *
 * @author dboat
 */
@Mapper
public interface DeviceMapper extends BaseMapper<Device> {
}

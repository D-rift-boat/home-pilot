package com.dboat.iot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dboat.iot.entity.DeviceAlarmLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备告警日志 Mapper 接口
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，自动获得 DeviceAlarmLog 实体的 CRUD 操作能力。
 * 告警记录由流式告警引擎异步写入，如需自定义查询可在 XML 中扩展。
 * </p>
 *
 * @author dboat
 */
@Mapper
public interface DeviceAlarmLogMapper extends BaseMapper<DeviceAlarmLog> {
}

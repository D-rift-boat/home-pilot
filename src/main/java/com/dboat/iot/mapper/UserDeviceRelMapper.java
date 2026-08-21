package com.dboat.iot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dboat.iot.entity.UserDeviceRel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-设备关系 Mapper 接口
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，自动获得 UserDeviceRel 实体的 CRUD 操作能力。
 * 提供根据 deviceId 查询订阅者列表的自定义 SQL 方法。
 * </p>
 *
 * @author dboat
 */
@Mapper
public interface UserDeviceRelMapper extends BaseMapper<UserDeviceRel> {

    /**
     * 根据设备ID查询所有订阅用户的ID列表
     *
     * @param deviceId 设备业务标识
     * @return 订阅该设备的用户ID集合
     */
    @Select("SELECT user_id FROM user_device_rel WHERE device_id = #{deviceId}")
    List<String> selectUserIdsByDeviceId(@Param("deviceId") String deviceId);

    /**
     * 根据用户ID查询所有订阅设备的ID列表
     * @param userId
     * @return List
     */
    @Select("SELECT device_id FROM user_device_rel WHERE user_id = #{userId}")
    List<String> selectDeviceIdsByUserId(@Param("userId") String userId);
}

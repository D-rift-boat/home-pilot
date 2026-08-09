package com.dboat.iot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dboat.iot.dto.request.CommandGetByIdReqDTO;
import com.dboat.iot.dto.request.CommandQueryReqDTO;
import com.dboat.iot.dto.request.CommandSendReqDTO;
import com.dboat.iot.dto.response.CommandRespDTO;
import com.dboat.iot.entity.DeviceCommand;

import java.util.List;

/**
 * 设备指令业务服务接口
 * <p>
 * 定义指令下发、查询、状态更新等操作。
 * 指令通过 MQTT 主题 device/command/{device_id} 发送到设备端。
 * </p>
 *
 * @author dboat
 */
public interface DeviceCommandService extends IService<DeviceCommand> {

    /**
     * 下发控制指令到指定设备（通过 MQTT 发送）
     *
     * @param request 指令下发请求 DTO
     * @return 指令记录信息
     */
    CommandRespDTO sendCommand(CommandSendReqDTO request);

    /**
     * 查询指定设备的所有指令记录（按时间倒序）
     *
     * @param request 查询请求 DTO
     * @return 指令列表
     */
    List<CommandRespDTO> getCommandsByDeviceId(CommandQueryReqDTO request);

    /**
     * 根据指令ID查询指令详情
     *
     * @param request 查询请求 DTO
     * @return 指令详情
     */
    CommandRespDTO getCommandById(CommandGetByIdReqDTO request);

    /**
     * 更新指令执行状态
     *
     * @param id     指令主键ID
     * @param status 新状态码（0=待下发, 1=已下发, 2=成功, 3=失败）
     */
    void updateCommandStatus(String id, int status);
}

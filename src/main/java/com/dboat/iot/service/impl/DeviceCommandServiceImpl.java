package com.dboat.iot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.iot.dto.request.CommandGetByIdReqDTO;
import com.dboat.iot.dto.request.CommandQueryReqDTO;
import com.dboat.iot.dto.request.CommandSendReqDTO;
import com.dboat.iot.dto.response.CommandRespDTO;
import com.dboat.iot.entity.DeviceCommand;
import com.dboat.iot.exception.BusinessException;
import com.dboat.iot.mapper.DeviceCommandMapper;
import com.dboat.iot.mqtt.MqttMessageHandler;
import com.dboat.iot.service.DeviceCommandService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 设备指令业务服务实现类
 * <p>
 * 实现指令下发、查询、状态更新等业务逻辑。
 * 指令下发时先写入 MySQL 记录，再通过 MQTT 发送到设备端。
 * </p>
 *
 * @author dboat
 */
@Service
public class DeviceCommandServiceImpl extends ServiceImpl<DeviceCommandMapper, DeviceCommand>
        implements DeviceCommandService {

    /** MQTT 消息处理器，用于发送指令到设备 */
    private final MqttMessageHandler mqttMessageHandler;

    /** 构造器注入 MQTT 消息处理器 */
    public DeviceCommandServiceImpl(MqttMessageHandler mqttMessageHandler) {
        this.mqttMessageHandler = mqttMessageHandler;
    }

    /**
     * 下发控制指令：先保存记录（状态=待下发），再通过 MQTT 发送，发送成功则更新状态=已下发
     */
    @Override
    public CommandRespDTO sendCommand(CommandSendReqDTO request) {
        DeviceCommand command = new DeviceCommand();
        command.setDeviceId(request.getDeviceId());
        command.setCommand(request.getCommand());
        command.setStatus(0); // Pending
        this.save(command);

        // Send via MQTT
        try {
            mqttMessageHandler.publishCommand(request.getDeviceId(), request.getCommand());
            command.setStatus(1); // Sent
            this.updateById(command);
        } catch (Exception e) {
            command.setStatus(3); // Failed
            this.updateById(command);
            throw new BusinessException("Failed to send command via MQTT: " + e.getMessage());
        }

        return toResponse(command);
    }

    @Override
    public List<CommandRespDTO> getCommandsByDeviceId(CommandQueryReqDTO request) {
        LambdaQueryWrapper<DeviceCommand> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceCommand::getDeviceId, request.getDeviceId());
        wrapper.orderByDesc(DeviceCommand::getCreateTime);
        return this.list(wrapper).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public CommandRespDTO getCommandById(CommandGetByIdReqDTO request) {
        DeviceCommand command = this.getById(request.getId());
        if (command == null) {
            throw new BusinessException("Command not found: " + request.getId());
        }
        return toResponse(command);
    }

    @Override
    public void updateCommandStatus(String id, int status) {
        DeviceCommand command = this.getById(id);
        if (command == null) {
            throw new BusinessException("Command not found: " + id);
        }
        command.setStatus(status);
        this.updateById(command);
    }

    /** 实体转响应 DTO */
    private CommandRespDTO toResponse(DeviceCommand command) {
        CommandRespDTO response = new CommandRespDTO();
        response.setId(command.getId());
        response.setDeviceId(command.getDeviceId());
        response.setCommand(command.getCommand());
        response.setStatus(command.getStatus());
        response.setCreateTime(command.getCreateTime());
        response.setUpdateTime(command.getUpdateTime());
        return response;
    }
}

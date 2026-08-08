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

@Service
public class DeviceCommandServiceImpl extends ServiceImpl<DeviceCommandMapper, DeviceCommand>
        implements DeviceCommandService {

    private final MqttMessageHandler mqttMessageHandler;

    public DeviceCommandServiceImpl(MqttMessageHandler mqttMessageHandler) {
        this.mqttMessageHandler = mqttMessageHandler;
    }

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

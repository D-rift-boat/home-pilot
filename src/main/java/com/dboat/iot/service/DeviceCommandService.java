package com.dboat.iot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dboat.iot.dto.request.CommandSendReqDTO;
import com.dboat.iot.dto.response.CommandRespDTO;
import com.dboat.iot.entity.DeviceCommand;

import java.util.List;

public interface DeviceCommandService extends IService<DeviceCommand> {

    CommandRespDTO sendCommand(CommandSendReqDTO request);

    List<CommandRespDTO> getCommandsByDeviceId(String deviceId);

    CommandRespDTO getCommandById(String id);

    void updateCommandStatus(String id, int status);
}

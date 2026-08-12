package com.dboat.iot.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dboat.iot.dto.request.*;
import com.dboat.iot.dto.response.DeviceRespDTO;
import com.dboat.iot.entity.Device;

/**
 * 设备资产业务服务接口
 * <p>
 * 定义设备 CRUD、自动注册、状态更新等业务操作。
 * 设备在线状态统一由 Redis 管理，不直接操作 MySQL 的 status 字段。
 * </p>
 *
 * @author dboat
 */
public interface DeviceService extends IService<Device> {

    /**
     * 手动创建设备
     *
     * @param request 设备创建请求 DTO
     * @return 创建成功的设备信息
     */
    DeviceRespDTO createDevice(DeviceCreateReqDTO request);

    /**
     * 更新设备静态属性
     *
     * @param request 设备更新请求 DTO
     * @return 更新后的设备信息
     */
    DeviceRespDTO updateDevice(DeviceUpdateReqDTO request);

    /**
     * 逻辑删除设备（同时清理 Redis 状态缓存）
     *
     * @param request 设备删除请求 DTO
     */
    void deleteDevice(DeviceDeleteReqDTO request);

    /**
     * 根据内部主键ID查询设备详情
     *
     * @param request 查询请求 DTO
     * @return 设备详情（含 Redis 实时状态）
     */
    DeviceRespDTO getDeviceById(DeviceGetByIdReqDTO request);

    /**
     * 根据设备业务标识查询设备详情
     *
     * @param request 查询请求 DTO
     * @return 设备详情（含 Redis 实时状态）
     */
    DeviceRespDTO getDeviceByDeviceId(DeviceGetByDeviceIdReqDTO request);

    /**
     * 分页查询设备列表
     *
     * @param request 分页查询请求 DTO
     * @return 分页结果
     */
    IPage<DeviceRespDTO> listDevices(DeviceListReqDTO request);

    /**
     * MQTT 消息处理时自动注册设备（如设备不存在则创建）
     *
     * @param deviceId 设备唯一标识
     * @return 设备实体
     */
    Device autoRegister(String deviceId);

    /**
     * 更新设备在线状态（通过 Redis 维护）
     *
     * @param deviceId 设备唯一标识
     * @param status   状态码（0=离线, 1=在线, 2=异常）
     */
    void updateStatus(String deviceId, int status);

    /**
     * 根据设备业务标识获取设备实体（内部调用，不抛异常）
     *
     * @param deviceId 设备唯一标识
     * @return 设备实体，不存在时返回 null
     */
    Device getDeviceByDeviceIdRaw(String deviceId);
}

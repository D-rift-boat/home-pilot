package com.dboat.iot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dboat.iot.entity.UserDeviceRel;

import java.util.List;
import java.util.Set;

/**
 * 用户-设备关系业务服务接口
 * <p>
 * 定义用户与设备订阅关系的管理操作，包括：
 * <ul>
 *   <li>查询设备的订阅者列表（优先从 Redis 缓存获取）</li>
 *   <li>创建/删除订阅关系</li>
 *   <li>管理 Redis 设备订阅者缓存</li>
 * </ul>
 * </p>
 *
 * @author dboat
 */
public interface UserDeviceRelService extends IService<UserDeviceRel> {

    /**
     * 获取订阅指定设备的所有用户ID集合
     * <p>
     * 优先从 Redis Set（iot:device:sub:{deviceId}）中读取，
     * 缓存未命中时回源查询 MySQL 数据库，并将结果回填到 Redis 缓存中。
     * </p>
     *
     * @param deviceId 设备业务标识
     * @return 订阅该设备的用户ID集合
     */
    Set<String> getSubscriberUserIds(String deviceId);

    /**
     * 订阅该用户订阅的所有设备ID集合
     * <p>
     * 优先从 Redis Set（iot:device:sub:{deviceId}）中读取，
     * 缓存未命中时回源查询 MySQL 数据库，并将结果回填到 Redis 缓存中。
     * </p>
     *
      * @param userId 用户ID
      * @return 订阅该用户订阅的所有设备ID集合
     */
    List<String> getSubscribedDeviceIds(String userId);

    /**
     * 刷新指定设备的 Redis 订阅者缓存
     * <p>
     * 从数据库查询最新订阅关系，覆盖写入 Redis Set。
     * 适用于订阅关系变更后主动刷新缓存。
     * </p>
     *
     * @param deviceId 设备业务标识
     */
    void refreshSubscriberCache(String deviceId);

    /**
     * 清除指定设备的 Redis 订阅者缓存
     *
     * @param deviceId 设备业务标识
     */
    void evictSubscriberCache(String deviceId);

    /**
     * 为用户添加设备订阅关系
     *
     * @param userId  用户ID
     * @param deviceId 设备业务标识
     * @param subType  订阅类型（1=拥有者, 2=共享订阅）
     */
    void addSubscription(String userId, String deviceId, Integer subType);

    /**
     * 移除用户的设备订阅关系
     *
     * @param userId   用户ID
     * @param deviceId 设备业务标识
     */
    void removeSubscription(String userId, String deviceId);
}

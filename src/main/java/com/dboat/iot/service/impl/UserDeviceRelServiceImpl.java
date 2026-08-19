package com.dboat.iot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.iot.entity.UserDeviceRel;
import com.dboat.iot.mapper.UserDeviceRelMapper;
import com.dboat.iot.service.UserDeviceRelService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.dboat.iot.common.constants.MqttConstants.IOT_DEVICE_SUB_PREFIX;

/**
 * 用户-设备关系业务服务实现类
 * <p>
 * 实现用户与设备订阅关系的管理，核心功能：
 * <ul>
 *   <li>设备订阅者列表查询：优先读 Redis Set（iot:device:sub:{deviceId}），
 *       缓存未命中时回源 MySQL 并回填 Redis</li>
 *   <li>订阅关系的增删操作，同步更新 Redis 缓存</li>
 * </ul>
 * </p>
 *
 * @author dboat
 */
@Service
public class UserDeviceRelServiceImpl extends ServiceImpl<UserDeviceRelMapper, UserDeviceRel> implements UserDeviceRelService {

    private static final Logger log = LoggerFactory.getLogger(UserDeviceRelServiceImpl.class);

    /**
     * Redis 模板，用于操作设备订阅者 Set 集合
     */
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 获取订阅指定设备的所有用户ID集合（Redis 优先，未命中则回源数据库）
     * <p>
     * 流程：
     * <ol>
     *   <li>检查 Redis Set（iot:device:sub:{deviceId}）是否存在且非空</li>
     *   <li>若存在，直接返回 Set 中的用户ID集合</li>
     *   <li>若不存在，查询 MySQL 数据库获取订阅者列表</li>
     *   <li>将查询结果写入 Redis Set 作为缓存，后续请求直接命中缓存</li>
     * </ol>
     * </p>
     *
     * @param deviceId 设备业务标识
     * @return 订阅该设备的用户ID集合
     */
    @Override
    public Set<String> getSubscriberUserIds(String deviceId) {
        String redisKey = IOT_DEVICE_SUB_PREFIX + deviceId;

        // 1. 优先从 Redis 读取订阅者列表
        Set<Object> cachedUserIds = redisTemplate.opsForSet().members(redisKey);
        if (cachedUserIds != null && !cachedUserIds.isEmpty()) {
            log.debug("Cache hit for device subscriber list: {}", deviceId);
            return cachedUserIds.stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());
        }

        // 2. Redis 未命中，回源查询数据库
        log.info("Cache miss for device subscriber list: {}, fallback to DB", deviceId);
        List<String> userIds = baseMapper.selectUserIdsByDeviceId(deviceId);

        // 3. 将查询结果回填到 Redis Set 缓存
        if (userIds != null && !userIds.isEmpty()) {
            String[] userIdArray = userIds.toArray(new String[0]);
            redisTemplate.opsForSet().add(redisKey, (Object[]) userIdArray);
            log.info("Cached {} subscribers for device [{}] to Redis", userIds.size(), deviceId);
        }

        return userIds != null ? Set.copyOf(userIds) : Set.of();
    }

    /**
     * 刷新指定设备的 Redis 订阅者缓存（从数据库全量覆盖）
     *
     * @param deviceId 设备业务标识
     */
    @Override
    public void refreshSubscriberCache(String deviceId) {
        String redisKey = IOT_DEVICE_SUB_PREFIX + deviceId;
        // 先删除旧缓存
        redisTemplate.delete(redisKey);
        // 从数据库查询最新数据并回填
        List<String> userIds = baseMapper.selectUserIdsByDeviceId(deviceId);
        if (userIds != null && !userIds.isEmpty()) {
            String[] userIdArray = userIds.toArray(new String[0]);
            redisTemplate.opsForSet().add(redisKey, (Object[]) userIdArray);
            log.info("Refreshed subscriber cache for device [{}], count={}", deviceId, userIds.size());
        }
    }

    /**
     * 清除指定设备的 Redis 订阅者缓存
     *
     * @param deviceId 设备业务标识
     */
    @Override
    public void evictSubscriberCache(String deviceId) {
        String redisKey = IOT_DEVICE_SUB_PREFIX + deviceId;
        redisTemplate.delete(redisKey);
        log.info("Evicted subscriber cache for device: {}", deviceId);
    }

    /**
     * 为用户添加设备订阅关系，同步更新 Redis 缓存
     *
     * @param userId   用户ID
     * @param deviceId 设备业务标识
     * @param subType  订阅类型（1=拥有者, 2=共享订阅）
     */
    @Override
    public void addSubscription(String userId, String deviceId, Integer subType) {
        // 检查是否已存在订阅关系
        LambdaQueryWrapper<UserDeviceRel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDeviceRel::getUserId, userId)
                .eq(UserDeviceRel::getDeviceId, deviceId);
        if (this.count(wrapper) > 0) {
            log.warn("Subscription already exists: userId={}, deviceId={}", userId, deviceId);
            return;
        }

        // 创建新的订阅关系
        UserDeviceRel rel = new UserDeviceRel();
        rel.setUserId(userId);
        rel.setDeviceId(deviceId);
        rel.setSubType(subType);
        this.save(rel);

        // 同步更新 Redis 缓存：将新用户加入 Set
        String redisKey = IOT_DEVICE_SUB_PREFIX + deviceId;
        redisTemplate.opsForSet().add(redisKey, userId);
        log.info("Added subscription: userId={}, deviceId={}, subType={}", userId, deviceId, subType);
    }

    /**
     * 移除用户的设备订阅关系，同步更新 Redis 缓存
     *
     * @param userId   用户ID
     * @param deviceId 设备业务标识
     */
    @Override
    public void removeSubscription(String userId, String deviceId) {
        // 从数据库删除
        LambdaQueryWrapper<UserDeviceRel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDeviceRel::getUserId, userId)
                .eq(UserDeviceRel::getDeviceId, deviceId);
        this.remove(wrapper);

        // 同步更新 Redis 缓存：从 Set 中移除该用户
        String redisKey = IOT_DEVICE_SUB_PREFIX + deviceId;
        redisTemplate.opsForSet().remove(redisKey, userId);
        log.info("Removed subscription: userId={}, deviceId={}", userId, deviceId);
    }
}

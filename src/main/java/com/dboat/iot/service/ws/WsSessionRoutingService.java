package com.dboat.iot.service.ws;

import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.dboat.iot.common.constants.WebSocketConstants.WS_ROUTER_PREFIX;
import static com.dboat.iot.common.constants.WebSocketConstants.WS_ROUTER_TTL_SECONDS;

/**
 * WS 会话路由存储
 * <p>
 * 管理 Redis 中 ws:session:{userId} Hash 的所有操作，包括：
 * <ul>
 *   <li>会话注册 / 移除（WS 连接生命周期）</li>
 *   <li>心跳续期（刷新 Hash 字段 + Key TTL）</li>
 *   <li>查询用户会话节点分布（供分布式推送使用）</li>
 *   <li>在线会话计数</li>
 * </ul>
 * 从 DeviceStateService 拆分而来，实现 WS 路由与 IoT 设备状态的解耦。
 * </p>
 *
 * @author dboat
 */
@Component
public class WsSessionRoutingService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // ==================== 会话写入 ====================

    /**
     * 注册/更新用户 WS 会话路由信息
     * <p>
     * 写入 Hash field=sessionId, value={nodeId, lastHeartbeatTs} JSON，
     * 并刷新整个 Key 的 TTL。
     * </p>
     *
     * @param userId    用户ID
     * @param sessionId WebSocket 会话ID
     * @param nodeId    当前节点ID
     */
    public void addSession(String userId, String sessionId, String nodeId) {
        String redisKey = WS_ROUTER_PREFIX + userId;
        JSONObject sessionMeta = new JSONObject();
        sessionMeta.put("nodeId", nodeId);
        sessionMeta.put("lastHeartbeatTs", String.valueOf(System.currentTimeMillis()));

        stringRedisTemplate.opsForHash().put(redisKey, sessionId, sessionMeta.toJSONString());
        stringRedisTemplate.expire(redisKey, WS_ROUTER_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 移除用户的指定会话路由
     *
     * @param userId    用户ID
     * @param sessionId WebSocket 会话ID
     */
    public void removeSession(String userId, String sessionId) {
        String redisKey = WS_ROUTER_PREFIX + userId;
        stringRedisTemplate.opsForHash().delete(redisKey, sessionId);
    }

    // ==================== 心跳续期 ====================

    /**
     * 刷新会话心跳时间戳及 Key TTL
     *
     * @param userId    用户ID
     * @param sessionId WebSocket 会话ID
     * @param nodeId    当前节点ID
     */
    public void refreshHeartbeat(String userId, String sessionId, String nodeId) {
        String redisKey = WS_ROUTER_PREFIX + userId;
        JSONObject sessionMeta = new JSONObject();
        sessionMeta.put("nodeId", nodeId);
        sessionMeta.put("lastHeartbeatTs", String.valueOf(System.currentTimeMillis()));

        stringRedisTemplate.opsForHash().put(redisKey, sessionId, sessionMeta.toJSONString());
        stringRedisTemplate.expire(redisKey, WS_ROUTER_TTL_SECONDS, TimeUnit.SECONDS);
    }

    // ==================== 查询 ====================

    /**
     * 获取用户所有 WS 会话的节点路由映射
     * <p>
     * 返回 sessionId → {nodeId, lastHeartbeatTs} 映射，
     * 供 {@link com.dboat.iot.service.ws.WsDistributedPushService} 判断用户在哪些节点有连接。
     * </p>
     *
     * @param userId 用户ID
     * @return 会话节点映射，Key 不存在时返回空 Map
     */
    public Map<String, JSONObject> getUserSessionNodeMap(String userId) {
        String redisKey = WS_ROUTER_PREFIX + userId;

        Map<String, JSONObject> entries = stringRedisTemplate.opsForHash().entries(redisKey).entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().toString(),
                        entry -> JSONObject.parseObject(entry.getValue().toString())
                ));
        if (ObjectUtils.isEmpty(entries)) {
            entries = Collections.emptyMap();
        }
        return entries;
    }

    /**
     * 获取用户在线 WS 会话数（Hash 字段数）
     *
     * @param userId 用户ID
     * @return 在线会话数，Key 不存在时返回 0
     */
    public Long getUserSessionCount(String userId) {
        String redisKey = WS_ROUTER_PREFIX + userId;
        Long count = stringRedisTemplate.opsForHash().size(redisKey);
        return count == null ? 0L : count;
    }

    /**
     * 获取用户所有在线会话ID集合
     *
     * @param userId 用户ID
     * @return 会话ID集合
     */
    public Set<String> getUserSessionIds(String userId) {
        String redisKey = WS_ROUTER_PREFIX + userId;
        return stringRedisTemplate.opsForHash().keys(redisKey).stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}

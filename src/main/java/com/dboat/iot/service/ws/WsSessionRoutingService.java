package com.dboat.iot.service.ws;

import com.dboat.iot.common.constants.RedisLuaConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.redisson.api.RBatch;
import org.redisson.api.RScript;
import org.redisson.api.RScriptAsync;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.dboat.iot.common.constants.WebSocketConstants.*;

/**
 * WS 会话路由存储
 * <p>
 * Redis存储模型：
 * <ul>
 * <li>ws:on:users                     ZSET 全局在线用户索引 member=userId score=过期时间戳ms</li>
 * <li>ws:on:user:ses:{userId}         ZSET 用户会话索引 member=sessionId score=过期时间戳ms (hash‑tag {userId})</li>
 * <li>ws:user:ses:{userId}            HASH 用户会话元数据 field=sessionId value=json(nodeId,lastHeartbeatTs) (hash‑tag {userId})</li>
 * </ul>
 * <p>
 * 约束：
 * 1. 心跳周期15s，会话TTL=50s，允许最多丢失3次心跳；
 * 2. hash key不设置TTL，不依赖redis自动过期；
 * 3. 过期判断完全依赖ZSET的score；<b>外部定时任务必须扫描ZSET，清理score小于当前时间的过期session，同步hdel hash内失效field</b>；
 * 4. ws:on:user:ses:{userId} 与 ws:user:ses:{userId} 使用相同hash‑tag，集群落到同一个slot；
 * </p>
 * 管理 Redis 中 ws:user:ses:{userId} Hash 以及配套zset索引的所有操作，包括：
 * <ul>
 *   <li>会话注册 / 移除（WS 连接生命周期）</li>
 *   <li>心跳续期（刷新 Hash字段 + 更新ZSET score）</li>
 *   <li>查询用户会话节点分布（供分布式推送使用）</li>
 *   <li>在线会话计数</li>
 * </ul>
 * 从 DeviceStateService 拆分而来，实现 WS 路由与 IoT 设备状态的解耦。
 * </p>
 *
 * @author dboat
 */
@Component
@Slf4j
public class WsSessionRoutingService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private DefaultRedisScript<Long> zaddWithExpireScript;

    @Resource
    private DefaultRedisScript<Long> hsetWithExpireScript;

    @Resource
    private RedissonClient redissonClient;


    // ==================== 会话写入 ====================

    /**
     * 注册用户 WS 会话路由信息
     * <p>
     * 1.写入Hash field=sessionId, value={nodeId, lastHeartbeatTs} JSON
     * 2.更新zset索引 ws:on:user:ses:{userId}、ws:on:users，score=当前时间+50s(ms)
     * 注意：不再给hash key设置expire，过期交给定时任务扫描zset清理
     * </p>
     *
     * @param userId    用户ID
     * @param sessionId WebSocket 会话ID
     * @param nodeId    当前节点ID
     */
    public void addSession(String userId, String sessionId, String nodeId) {
        long nowMs = System.currentTimeMillis();
        long expireTsMs = nowMs + WS_ROUTER_TTL_SECONDS * 1000L;

        String hashKey = String.format(WS_USER_SESSION_PREFIX, userId);
        String userSesZsetKey = String.format(WS_USER_ONLINE_SESSION_PREFIX, userId);

        // 写入hash会话元数据
        stringRedisTemplate.opsForHash().put(hashKey, sessionId, nodeId);

        // 参数顺序：keys, ARGV1=score(long), ARGV2=member, ARGV3=ttlSeconds
        stringRedisTemplate.execute(zaddWithExpireScript,
                Collections.singletonList(userSesZsetKey),
                String.valueOf(expireTsMs),             // ARGV[1] score zset分数
                sessionId,                              // ARGV[2] member
                String.valueOf(WS_ROUTER_TTL_SECONDS)   // ARGV[3] key ttl秒
        );

        // 更新全局在线用户zset索引
        stringRedisTemplate.execute(zaddWithExpireScript,
                Collections.singletonList(WS_ONLINE_USERS_KEY),
                String.valueOf(expireTsMs),
                userId,
                String.valueOf(WS_ROUTER_TTL_SECONDS)
        );
    }

    /**
     * 移除用户的指定会话路由
     * <p>
     * 1.hash删除sessionId field
     * 2.zset索引移除sessionId
     * 3.如果用户会话zset为空，则把userId从全局ws:on:users移除
     * </p>
     *
     * @param userId    用户ID
     * @param sessionId WebSocket 会话ID
     */
    public void removeSession(String userId, String sessionId) {
        String hashKey = String.format(WS_USER_SESSION_PREFIX, userId);
        String userSesZsetKey = String.format(WS_USER_ONLINE_SESSION_PREFIX, userId);

        // 删除hash内会话field
        stringRedisTemplate.opsForHash().delete(hashKey, sessionId);
        // 删除用户会话zset中的sessionId
        stringRedisTemplate.opsForZSet().remove(userSesZsetKey, sessionId);
    }

    // ==================== 心跳续期 ====================

    /**
     * 刷新会话心跳
     * <p>
     * 1.更新hash中session的meta（nodeId、lastHeartbeatTs）
     * 2.更新两处zset索引score为now+50s 过期时间
     * 如果 score < nowMs → 代表这个会话已经到期，应该清理。
     * 不操作hash key expire，过期完全由定时任务扫描zset score处理
     * </p>
     *
     * @param userId    用户ID
     * @param sessionId WebSocket 会话ID
     * @param nodeId    当前节点ID
     */
    public void refreshHeartbeat(String userId, String sessionId, String nodeId) {
        long nowMs = System.currentTimeMillis();
        long expireTsMs = nowMs + WS_ROUTER_TTL_SECONDS * 1000L;

        String userSesZsetKey = String.format(WS_USER_ONLINE_SESSION_PREFIX, userId);

        // 更新zset索引score（续期过期时间）
        RBatch batch = redissonClient.createBatch();
        RScriptAsync script = batch.getScript(StringCodec.INSTANCE);// 关键：指定StringCodec


        // 第一个脚本：用户会话zset
        script.evalAsync(
                RScript.Mode.READ_WRITE,
                RedisLuaConstants.LUA_ZADD_WITH_EXPIRE,
                RScript.ReturnType.INTEGER,
                Collections.singletonList(userSesZsetKey),
                expireTsMs, sessionId, String.valueOf(WS_ROUTER_TTL_SECONDS)
        );

        // 第二个脚本：全局zset
        script.evalAsync(
                RScript.Mode.READ_WRITE,
                RedisLuaConstants.LUA_ZADD_WITH_EXPIRE,
                RScript.ReturnType.INTEGER,
                Collections.singletonList(WS_ONLINE_USERS_KEY),
                expireTsMs, userId, String.valueOf(WS_ROUTER_TTL_SECONDS)
        );

        // 一次性网络发送
        batch.execute();
    }

    // ==================== 查询 ====================

    /**
     * 获取用户所有 WS 会话的节点路由映射
     * <p>
     * 返回 sessionId → {nodeId, lastHeartbeatTs} 映射，
     * 供 {@link com.dboat.iot.service.ws.WsDistributedPushService} 判断用户在哪些节点有连接。
     * <b>注意：本方法仅读取hash，不会过滤已经过期但尚未被定时任务清理的脏数据；上层或定时任务负责清理</b>
     * </p>
     *
     * @param userId 用户ID
     * @return 会话节点映射，Key 不存在时返回空 Map
     */
    public Map<String, String> getUserSessionNodeMap(String userId) {
        String hashKey = String.format(WS_USER_SESSION_PREFIX, userId);

        Map<String, String> entries = stringRedisTemplate.opsForHash().entries(hashKey).entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().toString(),
                        entry -> entry.getValue().toString()
                ));
        if (ObjectUtils.isEmpty(entries)) {
            entries = Collections.emptyMap();
        }
        return entries;
    }

    /**
     * 获取用户在线 WS 会话数（读取zset有效会话数量）
     * <p>
     * 数据源：ws:on:user:ses:{userId} zset。
     * 依赖定时巡检任务清理zset内超时僵尸数据，zset内member均为有效会话，保证计数准确。
     * </p>
     *
     * @param userId 用户ID
     * @return 在线会话数，zset不存在时返回 0
     */
    public Long getUserSessionCount(String userId) {
        String userSesZsetKey = String.format(WS_USER_ONLINE_SESSION_PREFIX, userId);
        Long count = stringRedisTemplate.opsForZSet().zCard(userSesZsetKey);
        return count == null ? 0L : count;
    }

    /**
     * 获取用户有效在线会话ID集合，从zset索引读取，定时任务已剔除过期member，数据可信
     *
     * @param userId 用户ID
     * @return 有效sessionId集合
     */
    public Set<String> getValidSessionIdsFromZset(String userId) {
        String userSesZsetKey = String.format(WS_USER_ONLINE_SESSION_PREFIX, userId);
        Set<String> sessionIds = stringRedisTemplate.opsForZSet().range(userSesZsetKey, 0, -1);
        return sessionIds == null ? Collections.emptySet() : sessionIds;
    }

    /**
     * 获取用户所有在线会话ID集合
     *
     * @param userId 用户ID
     * @return 会话ID集合
     */
    public Set<String> getUserSessionIds(String userId) {
        String hashKey = String.format(WS_USER_SESSION_PREFIX, userId);
        return stringRedisTemplate.opsForHash().keys(hashKey).stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

}

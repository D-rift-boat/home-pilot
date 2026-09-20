package com.dboat.iot.service.ws;

import com.alibaba.fastjson2.JSONObject;
import com.dboat.iot.common.constants.RedisLuaConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBatch;
import org.redisson.api.RScript;
import org.redisson.api.RScriptAsync;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.dboat.iot.common.constants.RedisLuaConstants.LUA_STRING_SET_WITH_EXPIRE;
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
 * 从 DeviceRedisService 拆分而来，实现 WS 路由与 IoT 设备状态的解耦。
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
    private RedissonClient redissonClient;

    // 分页大小，根据业务调，推荐50~200，不要太大
    private static final int BATCH_SIZE = 100;


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
     * @param json    json string
     */
    public void addSession(String userId, String sessionId, String json) {
        long nowMs = System.currentTimeMillis();
        long expireTsMs = nowMs + WS_EX_TTL_SECONDS * 1000L;

        String sessionIdKey = String.format(WS_CONN_PREFIX, sessionId);
        String userConnZsetKey = String.format(WS_USER_ONLINE_CONN_PREFIX, userId);

        // 写入string会话元数据
        // 参数顺序：keys, ARGV1=ts(long), ARGV2=member, ARGV3=ttlSeconds
        stringRedisTemplate.opsForValue().set(sessionIdKey, json.toString(), WS_EX_TTL_SECONDS, TimeUnit.SECONDS);

        // user zset 添加  参数顺序：keys, ARGV1=score(long), ARGV2=member, ARGV3=ttlSeconds
        stringRedisTemplate.execute(zaddWithExpireScript,
                Collections.singletonList(userConnZsetKey),
                String.valueOf(expireTsMs),             // ARGV[1] score zset分数
                sessionId,                              // ARGV[2] member
                String.valueOf(WS_EX_TTL_SECONDS)   // ARGV[3] key ttl秒
        );

        // 更新全局在线用户zset索引
        stringRedisTemplate.execute(zaddWithExpireScript,
                Collections.singletonList(WS_ONLINE_ALL_CONN_KEY),
                String.valueOf(nowMs),
                userId,
                String.valueOf(WS_EX_TTL_SECONDS)
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
    public void removeSession(String userId, String sessionId,String nodeId) {
        String sessionIdKey = String.format(WS_CONN_PREFIX, sessionId);
        String userConnZsetKey = String.format(WS_USER_ONLINE_CONN_PREFIX, userId);

        // 删除用户会话zset中的 sessionId
        stringRedisTemplate.opsForZSet().remove(userConnZsetKey, sessionId);
        // 删除 sessionIdKey
        stringRedisTemplate.delete(sessionIdKey);
        // 更新全局在线用户zset 不用更新 巡检任务会清理
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
        long expireTsMs = nowMs + WS_EX_TTL_SECONDS * 1000L;

        String sessionIdKey = String.format(WS_CONN_PREFIX, sessionId);
        String userConnZsetKey = String.format(WS_USER_ONLINE_CONN_PREFIX, userId);
        JSONObject json = new JSONObject();
        json.put("nodeId", nodeId);
        json.put("actiTs", nowMs);

        // 更新zset索引score（续期过期时间）
        RBatch batch = redissonClient.createBatch();
        RScriptAsync script = batch.getScript(StringCodec.INSTANCE);// 关键：指定StringCodec

        // 第一个脚本：hash刷新
        script.evalAsync(
                RScript.Mode.READ_WRITE,
                LUA_STRING_SET_WITH_EXPIRE,
                RScript.ReturnType.INTEGER,
                Collections.singletonList(sessionIdKey), // KEYS[1] = ws:session:sessxxx
                json.toString(),                                  // ARGV[1] value
                String.valueOf(WS_EX_TTL_SECONDS)        // ARGV[2] ttl秒
        );

        // 第二个脚本：用户会话zset
        script.evalAsync(
                RScript.Mode.READ_WRITE,
                RedisLuaConstants.LUA_ZADD_WITH_EXPIRE,
                RScript.ReturnType.INTEGER,
                Collections.singletonList(userConnZsetKey),
                expireTsMs, sessionId, String.valueOf(WS_EX_TTL_SECONDS)
        );

        // 第三个脚本：全局zset
        script.evalAsync(
                RScript.Mode.READ_WRITE,
                RedisLuaConstants.LUA_ZADD_WITH_EXPIRE,
                RScript.ReturnType.INTEGER,
                Collections.singletonList(WS_ONLINE_ALL_CONN_KEY),
                nowMs, userId, String.valueOf(WS_EX_TTL_SECONDS)
        );
        try {
            // 一次性网络发送
            // redisson batch + lua 逻辑
            batch.execute();
        } catch (Exception e) {
            log.error("[removeSession] redis lua batch execute failed, userId={}, sessionId={}",userId,sessionId,e);
            // metrics计数，不向上抛，交给定时巡检清理脏数据
        }
    }

    // ==================== 查询 ====================

    /**
     * 获取用户所有 WS 会话的节点路由映射
     * <p>
     * 返回 sessionId → {sessionId, nodeId} ，
     * 供 {@link com.dboat.iot.service.ws.WsDistributedPushService} 判断用户在哪些节点有连接。
     * <b>注意：本方法仅读取hash，不会过滤已经过期但尚未被定时任务清理的脏数据；上层或定时任务负责清理</b>
     * </p>
     *
     * @param userId 用户ID
     * @return 会话节点映射，Key 不存在时返回空 Map
     */
    public Set<String> getUserSessionNodeSet(String userId) {
        String zsetKey = String.format(WS_USER_ONLINE_CONN_PREFIX, userId);
        long now = System.currentTimeMillis();
        long expireTsMs = now + WS_EX_TTL_SECONDS * 1000L;

        //Set<ZSetOperations.TypedTuple<String>> entries = stringRedisTemplate.opsForZSet().rangeByScoreWithScores(zsetKey, now, expireTsMs);
        //HashMap<String, String> map = new HashMap<>();
        //for (ZSetOperations.TypedTuple<String> tuple : entries) {
        //    String member = tuple.getValue(); // 你的member：sessionId 或者 sessionId|nodeId
        //    //Double scoreDouble = tuple.getScore();
        //    //long scoreTs = scoreDouble.longValue(); // 心跳毫秒时间戳
        //    String[] parts = member.split("\\|");
        //    if (parts.length > 1) {
        //        String sessionId = parts[0];
        //        String nodeId = parts[1];
        //        map.put(sessionId, nodeId);
        //    }
        //}
        Set<String> nodeSet = new HashSet<>();
        long offset = 0;
        while (true) {
            // 【分页读取ZSet未过期数据  在线，带score】
            Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                    .rangeByScoreWithScores(zsetKey, now, expireTsMs, offset, BATCH_SIZE);
            if (tuples.isEmpty()) {
                break;
            }

            // 1. 组装 ws:session:{sessionId} key列表
            List<String> mgetKeys = new ArrayList<>(tuples.size());
            List<String> sessionIdList = new ArrayList<>(tuples.size());
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                String sessionId = tuple.getValue();
                sessionIdList.add(sessionId);
                String sessionKey = String.format(WS_CONN_PREFIX, sessionId);
                mgetKeys.add(sessionKey);
            }

            // 2. MGET批量查询string
            List<String> sessionList = stringRedisTemplate.opsForValue().multiGet(mgetKeys);

            // 3. 遍历结果，sessionId和nodeId一一对应处理
            for (int i = 0; i < sessionList.size(); i++) {
                String raw = sessionList.get(i);
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                JSONObject jsonObject;
                try {
                    jsonObject = JSONObject.parseObject(raw);
                } catch (Exception e) {
                    // json格式损坏，直接跳过
                    continue;
                }
                String nodeId = jsonObject.getString("nodeId");
                nodeSet.add(nodeId);
            }
            offset += BATCH_SIZE;
        }

        return nodeSet;
    }

    /**
     * 获取用户在线 WS 会话数（读取zset有效会话数量）
     * <p>
     * 数据源：ws:on:user:conn:{Uid} zset。
     * 依赖定时巡检任务清理zset内超时僵尸数据，zset内member均为有效会话，保证计数准确。
     * </p>
     *
     * @param userId 用户ID
     * @return 在线会话数，zset不存在时返回 0
     */
    public Long getUserSessionCount(String userId) {
        long now = System.currentTimeMillis();
        String userConnZsetKey = String.format(WS_USER_ONLINE_CONN_PREFIX, userId);
        Long count = (long) stringRedisTemplate.opsForZSet().rangeByScoreWithScores(userConnZsetKey, now, Double.POSITIVE_INFINITY).size();
        return count == null ? 0L : count;
    }

    /**
     * 获取用户有效在线会话ID集合，从zset索引读取，定时任务已剔除过期member，数据可信
     *
     * @param userId 用户ID
     * @return 有效sessionId集合
     */
    public Set<String> getValidSessionIdsFromZset(String userId) {
        String userConnZsetKey = String.format(WS_USER_ONLINE_CONN_PREFIX, userId);
        Set<String> sessionIds = stringRedisTemplate.opsForZSet().range(userConnZsetKey, 0, -1);
        return sessionIds == null ? Collections.emptySet() : sessionIds;
    }

    /**
     * 获取用户所有在线会话ID集合
     *
     * @param userId 用户ID
     * @return 会话ID集合
     */
    public Set<String> getUserSessionIds(String userId) {
        String hashKey = String.format(WS_CONN_PREFIX, userId);
        return stringRedisTemplate.opsForHash().keys(hashKey).stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

}

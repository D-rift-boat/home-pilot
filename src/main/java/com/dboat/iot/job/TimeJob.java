package com.dboat.iot.job;

import com.alibaba.fastjson2.JSONObject;
import com.dboat.iot.ws.DeviceWebSocketHandler;
import com.dboat.iot.ws.WsSession;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.dboat.iot.common.constants.WebSocketConstants.WS_HEARTBEAT_TIMEOUT_MILLIS;
import static com.dboat.iot.common.constants.WebSocketConstants.WS_ROUTER_PREFIX;

@Slf4j
@Component
public class TimeJob {

	@Autowired
	private RedissonClient redissonClient;

	private static final String INSPECTOR_LOCK_KEY = "ws:lock:session_inspector";
	private static final long LOCK_WAIT_TIME = 0;      // 拿不到锁立即放弃，不等待
	private static final long LOCK_LEASE_TIME = 120;   // 锁持有时间，大于巡检执行时间

	@Resource
	private RedisTemplate<String, Object> redisTemplate;

	@Resource
	private DeviceWebSocketHandler deviceWebSocketHandler;


	/**
	 * 定时清理本地缓存过期会话（每 30 秒执行一次）
	 * <p>
	 * 遍历内存 Map，检查对应 Redis 路由 Key 是否存活：
	 * - Key 不存在（已过期）→ 说明前端心跳中断，关闭 Session 并从 Map 移除
	 * - Key 存在 → 连接正常，跳过
	 * </p>
	 */
	@Scheduled(fixedRate = 30_000, initialDelay = 30_000)
	public void cleanExpiredSessions() {
		Map<String, WsSession> sessionMap = deviceWebSocketHandler.getWsSessionMap();
		Map<String, String> sessionRedisKeyMap = deviceWebSocketHandler.getWsSessionRedisKeyMap();

		if (sessionMap.isEmpty()) {
			return;
		}
		sessionMap.forEach((key, session) -> {
			if (System.currentTimeMillis() - session.getLastHeartbeatTime() > WS_HEARTBEAT_TIMEOUT_MILLIS) {
				// 清除本地超时会话
				sessionMap.remove(key);
				sessionRedisKeyMap.remove(session.getWebSocketSession().getId());
				// 清除 Redis 路由 Key
				//redisTemplate.delete(sessionRedisKeyMap.get(session.getWebSocketSession().getId()));
				log.debug("Cleaning expired WS session: mapKey={}, sessionId={}", key, session.getWebSocketSession().getId());
			}
		});
	}


	/**
	 * 定时检查并清理过期的 Redis 会话（每 30 秒执行一次）
	 * <p>
	 * 遍历 Redis 中所有匹配的 Key，检查其中的 Hash 字段：
	 * - 字段不存在（已过期）→ 说明前端心跳中断，删除该字段
	 * - 字段存在 → 连接正常，跳过
	 * </p>
	 */
	@Scheduled(fixedRate = 30_000)
	public void inspectTimeoutRedisSessions() {
		RLock lock = redissonClient.getLock(INSPECTOR_LOCK_KEY);

		// 尝试加锁，waitTime=0 拿不到立即返回false
		boolean locked = false;
		try {
			locked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
			if (!locked) {
				// 其他节点正在执行，本节点跳过
				return;
			}

			try {
				// 拿到锁，执行巡检
				doInspect();
			} catch (Exception e) {
				log.error("Error occurred during Redis session inspection", e);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return;
		} finally {
			// 释放锁（必须判断是否当前线程持有，防止误删）
			if (locked && lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		}
	}

	/**
	 * 执行redis巡检
	 */
	private void doInspect() {
		long now = System.currentTimeMillis();
		try (Cursor<String> cursor = redisTemplate.scan(
				ScanOptions.scanOptions().match(WS_ROUTER_PREFIX + "*").count(100).build())) {

			while (cursor.hasNext()) {
				String key = cursor.next();
				Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

				for (Map.Entry<Object, Object> entry : entries.entrySet()) {
					String sessionId = (String) entry.getKey();
					JSONObject meta = JSONObject.parseObject(entry.getValue().toString());
					long lastTs = meta.getLongValue("lastHeartbeatTs");

					if (now - lastTs > 60_000) {
						redisTemplate.opsForHash().delete(key, sessionId);
						// 未来加副作用放这里，有锁保证只执行一次
					}
				}

				if (redisTemplate.opsForHash().size(key) == 0) {
					redisTemplate.delete(key);
				}
			}
		}
	}

}

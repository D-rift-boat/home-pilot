package com.dboat.iot.job;

import com.alibaba.fastjson2.JSONObject;
import com.dboat.iot.dto.ws.IotDevLineDTO;
import com.dboat.iot.utils.DeviceStateStore;
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
import org.springframework.web.socket.CloseStatus;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.dboat.iot.common.constants.MqttConstants.IOT_DEVICE_OFFLINE_TIMEOUT_MS;
import static com.dboat.iot.common.constants.MqttConstants.IOT_DEVICE_ONLINE_PREFIX;
import static com.dboat.iot.common.constants.WebSocketConstants.WS_HEARTBEAT_TIMEOUT_MILLIS;
import static com.dboat.iot.common.constants.WebSocketConstants.WS_ROUTER_PREFIX;

@Slf4j
@Component
public class TimeJob {

	@Autowired
	private RedissonClient redissonClient;

	private static final String INSPECTOR_LOCK_KEY = "ws:lock:session_inspector";
	private static final String IOT_OFFLINE_LOCK_KEY = "iot:lock:device_offline_inspector";
	private static final long LOCK_WAIT_TIME = 0;      // 拿不到锁立即放弃，不等待
	private static final long LOCK_LEASE_TIME = 120;   // 锁持有时间，大于巡检执行时间

	@Resource
	private RedisTemplate<String, Object> redisTemplate;

	@Resource
	private DeviceWebSocketHandler deviceWebSocketHandler;

	@Resource
	private DeviceStateStore deviceStateStore;

	/**
	 * 定时清理过期iot设备在线列表
	 */



	/**
	 * 定时清理本地缓存过期会话（每 15 秒执行一次）
	 * <p>
	 * 遍历内存 Map，检查对应 Redis 路由 Key 是否存活：
	 * - Key 不存在（已过期）→ 说明前端心跳中断，关闭 Session 并从 Map 移除
	 * - Key 存在 → 连接正常，跳过
	 * </p>
	 */
	@Scheduled(fixedRate = 15_000, initialDelay = 15_000)
	public void cleanLocalExpiredSessions() {
		Map<String, WsSession> sessionMap = deviceWebSocketHandler.getWsSessionMap();
		Map<String, String> sessionRedisKeyMap = deviceWebSocketHandler.getWsSessionRedisKeyMap();

		if (sessionMap.isEmpty()) {
			return;
		}
		sessionMap.forEach((key, session) -> {
			if (System.currentTimeMillis() - session.getLastHeartbeatTime() > WS_HEARTBEAT_TIMEOUT_MILLIS) {
				// 1.先尝试close（可能触发onClose，也可能不触发）
				try {
					if (session.getWebSocketSession().isOpen()) {
						session.getWebSocketSession().close(CloseStatus.GOING_AWAY);
					}
				} catch (Exception e) {
					// ps：即使关闭失败，内存也不会泄漏，因为本地 sessionMap 已经删了，session 对象失去引用被 GC 回收
					log.warn("关闭超时session失败, sessionId={}", session.getWebSocketSession().getId(), e);
				}
				// 清除本地超时会话
				sessionMap.remove(key);
				sessionRedisKeyMap.remove(session.getWebSocketSession().getId());
				// 清除 Redis 在线会话列表中的该会话
				//redisTemplate.delete(sessionRedisKeyMap.get(session.getWebSocketSession().getId()));
				try {
					//TODO 可以考虑异步执行
					redisTemplate.opsForHash().delete(key, session.getWebSocketSession().getId());
				} catch (Exception e) {
					log.error("Error occurred while deleting session from Redis, key={}, sessionId={}", key, session.getWebSocketSession().getId(), e);
				}
				log.debug("Cleaning expired WS session: mapKey={}, sessionId={}", key, session.getWebSocketSession().getId());
			}
		});
	}


	/**
	 * 定时检查并清理过期的 Redis 会话（每 65 秒执行一次）
	 * <p>
	 * 遍历 Redis 中所有匹配的 Key，检查其中的 Hash 字段：
	 * - 字段不存在（已过期）→ 说明前端心跳中断，删除该字段
	 * - 字段存在 → 连接正常，跳过
	 * </p>
	 */
	@Scheduled(fixedRate = 15_000)
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
			log.error("Redis session inspection interrupted", e);
			Thread.currentThread().interrupt();
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

					if (now - lastTs > WS_HEARTBEAT_TIMEOUT_MILLIS) {
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

	// ==================== IoT 设备离线巡检 ====================

	/**
	 * 定时巡检 IoT 设备离线状态（每 15 秒执行一次）
	 * <p>
	 * 使用 Redisson 分布式锁保证多实例部署下只有一个节点执行巡检。
	 * 扫描 Redis 中所有 iot:device:online:{userId} Hash Key，
	 * 检查每个设备的 lastReportTs，若 now - lastReportTs > 60s 则判定业务离线。
	 * </p>
	 */
	@Scheduled(fixedRate = 15_000, initialDelay = 15_000)
	public void iotDeviceOfflineInspect() {
		RLock lock = redissonClient.getLock(IOT_OFFLINE_LOCK_KEY);

		boolean locked = false;
		try {
			locked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
			if (!locked) {
				// 其他节点正在执行，本节点跳过
				return;
			}

			try {
				doIotOfflineInspect();
			} catch (Exception e) {
				log.error("IoT device offline inspection error", e);
			}
		} catch (InterruptedException e) {
			log.error("IoT device offline inspection interrupted", e);
			Thread.currentThread().interrupt();
		} finally {
			if (locked && lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		}
	}

	/**
	 * 执行 IoT 设备离线巡检
	 * <p>
	 * 扫描所有 iot:device:online:{userId} Hash Key，
	 * 对每个 Hash 条目检查 lastReportTs：
	 * <ul>
	 *   <li>now - lastReportTs > 60s → 判定业务离线，从 Hash 中移除，触发 WS 推送 + 离线日志</li>
	 *   <li>now - lastReportTs <= 60s → 设备活跃，跳过</li>
	 * </ul>
	 * 若 Hash 清空（所有设备离线），则删除整个 Key。
	 * </p>
	 */
	private void doIotOfflineInspect() {
		long now = System.currentTimeMillis();
		int offlineCount = 0;

		try (Cursor<String> cursor = redisTemplate.scan(
				ScanOptions.scanOptions().match(IOT_DEVICE_ONLINE_PREFIX + "*").count(100).build())) {

			while (cursor.hasNext()) {
				String key = cursor.next();
				Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

				for (Map.Entry<Object, Object> entry : entries.entrySet()) {
					String deviceId = (String) entry.getKey();
					IotDevLineDTO devLine = JSONObject.parseObject(entry.getValue().toString(), IotDevLineDTO.class);

					if (devLine == null || devLine.getLastReportTs() == null) {
						continue;
					}

					long lastReportTs = Long.parseLong(devLine.getLastReportTs());

					// 核心判定：now - lastReportTs > 60s → 业务离线
					if (now - lastReportTs > IOT_DEVICE_OFFLINE_TIMEOUT_MS) {
						// 1. 从 Redis Hash 中移除该离线设备
						redisTemplate.opsForHash().delete(key, deviceId);

						// 2. WS 推送离线事件 + 记录离线日志 + 更新在线计数
						deviceStateStore.iotDeviceOffline(deviceId);

						offlineCount++;
						log.info("IoT device offline detected by inspection: deviceId={}, lastReportTs={}, idle={}ms",
								deviceId, lastReportTs, now - lastReportTs);
					}
				}

				// Hash 清空后删除整个 Key
				if (redisTemplate.opsForHash().size(key) == 0) {
					redisTemplate.delete(key);
				}
			}
		}

		if (offlineCount > 0) {
			log.info("IoT device offline inspection completed: {} device(s) marked offline", offlineCount);
		}
	}

}

package com.dboat.iot.job;

import com.alibaba.fastjson2.JSONObject;
import com.dboat.iot.dto.ws.IotDevLineDTO;
import com.dboat.iot.dto.ws.WsUploadDataDTO;
import com.dboat.iot.enums.WsTypeEnum;
import com.dboat.iot.service.UserDeviceRelService;
import com.dboat.iot.service.ws.WsDistributedPushService;
import com.dboat.iot.utils.DeviceStateService;
import com.dboat.iot.utils.WsSessionRoutingService;
import com.dboat.iot.ws.LocalWsSessionManager;
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
import java.util.Set;
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
	private LocalWsSessionManager localWsSessionManager;

	@Resource
	private DeviceStateService deviceStateService;

	/** WS 会话路由存储，管理 Redis 路由表操作 */
	@Resource
	private WsSessionRoutingService wsSessionRoutingService;

	/** WS 分布式推送服务，用户级精准推送 */
	@Resource
	private WsDistributedPushService wsPushService;

	/** 用户-设备关系服务，用于查询设备订阅者列表 */
	@Resource
	private UserDeviceRelService userDeviceRelService;

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
		Map<String, WsSession> sessionMap = localWsSessionManager.getSessionMap();

		if (sessionMap.isEmpty()) {
			return;
		}
		sessionMap.forEach((sessionId, wsSession) -> {
			if (System.currentTimeMillis() - wsSession.getLastHeartbeatTime() > WS_HEARTBEAT_TIMEOUT_MILLIS) {
				// 1.先尝试close（可能触发onClose，也可能不触发）
				try {
					if (wsSession.getWebSocketSession().isOpen()) {
						wsSession.getWebSocketSession().close(CloseStatus.GOING_AWAY);
					}
				} catch (Exception e) {
					// ps：即使关闭失败，内存也不会泄漏，因为本地 sessionMap 已经删了，session 对象失去引用被 GC 回收
					log.warn("关闭超时session失败, sessionId={}", wsSession.getWebSocketSession().getId(), e);
				}
				// 通过 LocalWsSessionManager 移除本地超时会话 + redisKey 映射
				String redisKey = localWsSessionManager.getRedisKey(sessionId);
				localWsSessionManager.removeSession(sessionId);
				// 通过 WsSessionRoutingService 清除 Redis 路由表中的该会话
				String userId = wsSession.getUserId();
				if (userId != null) {
					try {
						wsSessionRoutingService.removeSession(userId, sessionId);
					} catch (Exception e) {
						log.error("Error occurred while deleting session from Redis routing store, userId={}, sessionId={}", userId, sessionId, e);
					}
				}
				log.debug("Cleaning expired WS session: sessionId={}", sessionId);
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
						// 1. 更新 Redis 在线列表（纯 Redis 操作，返回剩余在线数）
						deviceStateService.iotDeviceOffline(deviceId);

						// 2. 构建 WS 事件并推送给订阅用户
						Set<String> subscriberUserIds = userDeviceRelService.getSubscriberUserIds(deviceId);
						for (String userId : subscriberUserIds) {
							long remainingCount = deviceStateService.getUserIotDeviceOnlineCount(userId);
							WsUploadDataDTO wsUploadDataDTO = new WsUploadDataDTO();
							wsUploadDataDTO.setType(WsTypeEnum.IOT_DEVICE_ONLINE_COUNT.getCode());
							WsUploadDataDTO.DataDTO dataDTO = new WsUploadDataDTO.DataDTO();
							WsUploadDataDTO.DeviceDTO deviceDTO = new WsUploadDataDTO.DeviceDTO();
							deviceDTO.setDeviceId(deviceId);
							dataDTO.setIotDeviceOnlineCount(Integer.valueOf(String.valueOf(remainingCount)));
							wsUploadDataDTO.setData(dataDTO);
							wsUploadDataDTO.setDevice(deviceDTO);
							wsPushService.pushToUser(userId, WsTypeEnum.IOT_DEVICE_ONLINE_COUNT.getCode(), wsUploadDataDTO);
						}

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

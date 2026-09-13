package com.dboat.iot.job;

import com.alibaba.fastjson2.JSONObject;
import com.dboat.iot.dto.ws.IotDevLineDTO;
import com.dboat.iot.dto.ws.WsUploadDataDTO;
import com.dboat.iot.enums.WsTypeEnum;
import com.dboat.iot.service.UserDeviceRelService;
import com.dboat.iot.service.ws.WsDistributedPushService;
import com.dboat.iot.service.ws.DeviceStateService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.dboat.iot.common.constants.MqttConstants.IOT_DEVICE_OFFLINE_TIMEOUT_MS;
import static com.dboat.iot.common.constants.MqttConstants.IOT_DEV_SHADOW;
import static com.dboat.iot.common.constants.WebSocketConstants.*;

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
	private DeviceStateService deviceStateService;

	/** WS 分布式推送服务，用户级精准推送 */
	@Resource
	private WsDistributedPushService wsPushService;

	/** 用户-设备关系服务，用于查询设备订阅者列表 */
	@Resource
	private UserDeviceRelService userDeviceRelService;

	/**
	 * stringRedisTemplate
	 */
	@Resource
	private StringRedisTemplate stringRedisTemplate;

	// Lua脚本
	//@Resource
	//private DefaultRedisScript<Long> checkZsetEmptyOnlyDelHashScript;

	//@Resource
	//private DefaultRedisScript<Long> hashCleanExpiredSessionScript;
	
	@Resource
	private DefaultRedisScript<List> cleanExpiredUserConnScript;

	/**
	 * 定时检查并清理过期的 Redis ws会话（每 15 秒执行一次,redis 会话过期时间 50s 僵尸会话最大存在65s）
	 * <p>
	 * 使用 Redisson 分布式锁保证多实例部署下只有一个节点执行巡检。
	 * 遍历 Redis 中所有匹配的 Key，检查其中的 Hash 字段：
	 * - 字段不存在（已过期）→ 说明前端心跳中断，删除该字段
	 * - 字段存在 → 连接正常，跳过
	 * </p>
	 */
	@Scheduled(fixedRate = 45_000)
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
				long cleanedSessionCount = doRedisWsInspect();
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
	 * 执行redis websession 会话数据巡检清理逻辑
	 * <p>
	 * 由外部定时任务调用，外部保证分布式锁，保证集群只有单个节点执行；
	 * 过期判断完全依赖zset score，不读取hash内部时间字段。
	 * zset 里面存的是过期时间戳 = 当前心跳时间 +50_000ms
	 * 如果 score < nowMs → 代表这个会话已经到期，应该清理。
	 * </p>
	 *
	 * @return 本次清理掉的僵尸session总数量
	 */
	public long doRedisWsInspect() {
		long nowMs = System.currentTimeMillis();
		long totalClean = 0L;

		//1. 全局在线用户zset：取出所有已经过期的uid（score < nowMs）
		Set<String> expireUidSet = stringRedisTemplate.opsForZSet().rangeByScore(WS_ONLINE_ALL_CONN_KEY, 0, nowMs);
		if (ObjectUtils.isEmpty(expireUidSet)) {
			return 0L;
		}

		// 全局在线用户索引 不用删除 ，删除容易出现并发竞态问题
		for (String uid : expireUidSet) {
			String userConnZsetKey = String.format(WS_USER_ONLINE_CONN_PREFIX, uid);
			//2. 获取该用户zset里面所有过期sessionId
			List<Object> cleanConnRes = stringRedisTemplate.execute(cleanExpiredUserConnScript,
					List.of(userConnZsetKey),
					String.valueOf(nowMs)
			);
			List connList = (List) cleanConnRes.get(0);
			Integer cleanConnCount = Integer.valueOf(cleanConnRes.get(1).toString());
			long cleanCnt = connList.size();
			totalClean += cleanCnt;

			//3. 批量删除过期session string  批量执行  删不掉也无所谓 ex 到期会自动清除
			if (!connList.isEmpty()) {
				stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
					for (Object sid : connList) {
						byte[] rawKey = (WS_CONN_PREFIX + sid).getBytes(StandardCharsets.UTF_8);
						connection.del(rawKey);
					}
					return null;
				});
			}
			if (cleanConnCount > 0){
				WsUploadDataDTO wsUploadDataDTO = new WsUploadDataDTO();
				wsUploadDataDTO.setType("USER_DEVICE_ONLINE_COUNT");
				WsUploadDataDTO.DataDTO dataDTO = new WsUploadDataDTO.DataDTO();
				WsUploadDataDTO.DeviceDTO deviceDTO = new WsUploadDataDTO.DeviceDTO();
				deviceDTO.setDeviceId("web-001");
				dataDTO.setUserDeviceOnlineCount(Integer.valueOf(String.valueOf(cleanConnCount)));
				wsUploadDataDTO.setData((dataDTO));
				wsUploadDataDTO.setDevice(deviceDTO);
				wsUploadDataDTO.setData((dataDTO));
				wsPushService.pushToUser(uid, WS_USER_ONLINE_CONN_PREFIX, wsUploadDataDTO);
			}

		}
		log.info("ws会话巡检执行完成，本次清理僵尸会话数量:{}", totalClean);
		return totalClean;
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
				ScanOptions.scanOptions().match(IOT_DEV_SHADOW + "*").count(100).build())) {

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

package com.dboat.iot.config;

import com.dboat.iot.common.constants.RedisLuaConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.Set;

/**
 * Redis Lua 脚本配置类
 */
@Configuration
public class RedisLuaScriptConfig {

    /**
     * 创建一个 Redis 脚本，用于在设置元素到有序集合时设置过期时间
     *
     * @return DefaultRedisScript<Long>
     */
    @Bean
    public DefaultRedisScript<Long> zaddWithExpireScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(RedisLuaConstants.LUA_ZADD_WITH_EXPIRE);
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 创建一个 Redis 脚本，用于在设置哈希字段时设置过期时间
     *
     * @return DefaultRedisScript<Long>
     */
    @Bean
    public DefaultRedisScript<Long> hsetWithExpireScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(RedisLuaConstants.LUA_HSET_WITH_EXPIRE);
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 创建一个 Redis 脚本，用于检查有序集合是否为空，如果为空则删除关联的哈希表
     *
     * @return DefaultRedisScript<Long>
     */
    @Bean
    public DefaultRedisScript<Long> checkZsetEmptyOnlyDelHashScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(RedisLuaConstants.CHECK_ZSET_EMPTY_ONLY_DEL_HASH_LUA);
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 创建一个 Redis 脚本，用于检查有序集合是否为空，如果为空则删除关联的哈希表
     *
     * @return DefaultRedisScript<Long>
     */
    @Bean
    public DefaultRedisScript<Long> checkZsetEmptyDelZsetScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(RedisLuaConstants.CHECK_ZSET_EMPTY_DEL_ZSET_LUA);
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 创建一个 Redis 脚本，删除hash、zset
     *
     * @return DefaultRedisScript<Long>
     */
    @Bean
    public DefaultRedisScript<Long> removeSessionScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(RedisLuaConstants.LUA_REMOVE_SESSION);
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 创建一个 Redis 脚本，清除hash 中过期数据
     *
     * @return DefaultRedisScript<Long>
     */
    @Bean
    public DefaultRedisScript<Long> hashCleanExpiredSessionScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(RedisLuaConstants.LUA_HASH_CLEAN_EXPIRED_SESSION);
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 创建一个 Redis 脚本，清除user zset中的 过期连接
     *
     * @return DefaultRedisScript<Long>
     */
    @Bean
    public DefaultRedisScript<List> cleanExpiredUserConnScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(RedisLuaConstants.CLEAN_EX_USER_CONN_LUA);
        script.setResultType(List.class);
        return script;
    }

    /**
     * 创建一个 Redis 脚本，如果成员过期 则删除
     *
     * @return DefaultRedisScript<Long>
     */
    @Bean
    public DefaultRedisScript<Long> zremIfMemExScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(RedisLuaConstants.ZREM_IF_MEM_EX_LUA);
        script.setResultType(Long.class);
        return script;
    }


    //=========================== iot lua =============================
    /**
     * device heartbeat Lua script
     * KEYS[1]: iot device heartbeat key
     * ARGV[1]: heartbeat timestamp
     * ARGV[2]: expire time  s
     * return 1: updated; 0: not updated
     * @return DefaultRedisScript<Long>
     */
    @Bean
    public DefaultRedisScript<Long> iotDevActiveUpdateScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(RedisLuaConstants.LUA_UPDATE_DEV_ACTIVE);
        script.setResultType(Long.class);
        return script;
    }

    /**
     * ZSet分组设备成员条件更新score脚本；ts<=原score拒绝更新
     * 返回 Long:1成功，0拒绝
     */
    @Bean
    public DefaultRedisScript<Long> compareScoreUpdateGroupDevScript() {
        String lua = """
                local zKey = KEYS[1]
                local member = ARGV[1]
                local newScore = tonumber(ARGV[2])
                local oldScore = redis.call('ZSCORE', zKey, member)
                if oldScore == false then
                    redis.call('ZADD', zKey, newScore, member)
                    return 1
                end
                oldScore = tonumber(oldScore)
                if newScore <= oldScore then
                    return 0
                end
                redis.call('ZADD', zKey, newScore, member)
                return 1
                """;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 设备全局分桶ZSet条件更新脚本（活跃时间戳校验，滞后消息拒绝更新）
     * 返回 Long：1成功；0时序落后拒绝
     */
    @Bean
    public DefaultRedisScript<Long> devGlobalBucketZsetUpdateScript() {
        String lua = """
            local zKey = KEYS[1]
            local member = ARGV[1]
            local newScore = tonumber(ARGV[2])
            local oldScore = redis.call('ZSCORE', zKey, member)
            if oldScore == false then
                redis.call('ZADD', zKey, newScore, member)
                return 1
            end
            oldScore = tonumber(oldScore)
            if newScore <= oldScore then
                return 0
            end
            redis.call('ZADD', zKey, newScore, member)
            return 1
            """;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(Long.class);
        return script;
    }

    /**
     * ZSet条件删除：仅入参ts大于原有score才执行ZREM；防止滞后离线事件误删分组设备成员
     * 返回 Long：1已删除；0时序拒绝；2成员不存在
     */
    @Bean
    public DefaultRedisScript<Long> groupDevZsetCompareTsRemoveScript() {
        String lua = """
            local zKey = KEYS[1]
            local member = ARGV[1]
            local newTs = tonumber(ARGV[2])
            local oldScore = redis.call('ZSCORE', zKey, member)
            if oldScore == false then
                return 2
            end
            oldScore = tonumber(oldScore)
            if newTs <= oldScore then
                return 0
            end
            redis.call('ZREM', zKey, member)
            return 1
            """;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 全局分桶ZSet条件删除脚本：仅入参事件ts大于原有score才ZREM，防止滞后离线事件误删
     * 返回 Long：1已删除；0时序拒绝；2成员不存在
     */
    @Bean
    public DefaultRedisScript<Long> devGlobalBucketZsetCompareTsRemoveScript() {
        String lua = """
            local zKey = KEYS[1]
            local member = ARGV[1]
            local newTs = tonumber(ARGV[2])
            local oldScore = redis.call('ZSCORE', zKey, member)
            if oldScore == false then
                return 2
            end
            oldScore = tonumber(oldScore)
            if newTs <= oldScore then
                return 0
            end
            redis.call('ZREM', zKey, member)
            return 1
            """;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 全局心跳分桶巡检清理僵尸设备脚本
     * 原子查询并且删除 score < thresholdTs 的member，返回被删除deviceId列表
     */
    @Bean
    public DefaultRedisScript<List> globalBucketInspectExpireScript() {
        String lua = """
                local zKey = KEYS[1]
                local threshold = tonumber(ARGV[1])
                local expiredMembers = redis.call('ZRANGEBYSCORE', zKey, '-inf', threshold)
                if #expiredMembers == 0 then
                    return {}
                end
                local batchSize = 200
                local total = #expiredMembers
                local idx = 1
                while idx <= total do
                    local slice = {}
                    for i = 0, batchSize - 1 do
                        local pos = idx + i
                        if pos > total then
                            break
                        end
                        table.insert(slice, expiredMembers[pos])
                    end
                    redis.call('ZREM', zKey, unpack(slice))
                    idx = idx + batchSize
                end
                return expiredMembers
            """;
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(List.class);
        return script;
    }




}

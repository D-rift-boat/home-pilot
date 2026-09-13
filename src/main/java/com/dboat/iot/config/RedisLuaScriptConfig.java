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
    public DefaultRedisScript<Integer> iotDevHeartbeatScript() {
        DefaultRedisScript<Integer> script = new DefaultRedisScript<>();
        script.setScriptText(RedisLuaConstants.LUA_IOT_DEV_HEARTBEAT);
        script.setResultType(Integer.class);
        return script;
    }

}

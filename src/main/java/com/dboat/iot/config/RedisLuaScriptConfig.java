package com.dboat.iot.config;

import com.dboat.iot.common.constants.RedisLuaConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;

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
}

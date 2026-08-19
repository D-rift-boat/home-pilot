package com.dboat.iot.utils;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RedissonLuaUtil {

    @Resource
    private RedissonClient redissonClient;

    /**
     * 执行Lua脚本，返回Long整数
     * @param scriptContent lua脚本原文
     * @param keys KEYS[]数组（String）
     * @param args ARGV[]参数，全部传String
     * @return 返回数字结果
     */
    public Long evalLong(String scriptContent, List<String> keys, Object... args) {
        RScript script = redissonClient.getScript(StringCodec.INSTANCE);
        try {
            // ✅把List<String>转成List<Object>解决泛型编译报错
            List<Object> objKeys = keys.stream().map(k -> (Object)k).collect(Collectors.toList());
            return script.eval(
                    RScript.Mode.READ_WRITE,
                    scriptContent,
                    RScript.ReturnType.INTEGER,
                    objKeys,
                    args
            );
        } catch (Exception e) {
            log.error("[RedissonLua] lua脚本执行异常 keys={}", keys, e);
            throw new RuntimeException("redis lua execute fail", e);
        }
    }

    /**
     * 返回String类型
     */
    public String evalString(String scriptContent, List<String> keys, Object... args) {
        RScript script = redissonClient.getScript(StringCodec.INSTANCE);
        try {
            List<Object> objKeys = keys.stream().map(k -> (Object)k).collect(Collectors.toList());
            return script.eval(RScript.Mode.READ_WRITE,
                    scriptContent,
                    RScript.ReturnType.VALUE,
                    objKeys, args);
        } catch (Exception e) {
            log.error("[RedissonLua] lua脚本执行异常 keys={}", keys, e);
            throw new RuntimeException("redis lua execute fail", e);
        }
    }
}
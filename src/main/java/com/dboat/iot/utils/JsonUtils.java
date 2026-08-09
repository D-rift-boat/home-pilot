package com.dboat.iot.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

/**
 * JSON 工具类 —— 基于 Fastjson2 封装
 * <p>
 * 提供 JSON 字符串与对象之间的序列化/反序列化操作，
 * 统一项目中的 JSON 处理方式，避免直接依赖具体实现库。
 * </p>
 *
 * @author dboat
 */
public class JsonUtils {

    /** 私有构造方法，防止实例化（工具类纯静态方法） */
    private JsonUtils() {}

    /**
     * 将 JSON 字符串反序列化为指定类型对象
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @param <T>   泛型类型参数
     * @return 反序列化后的对象
     */
    public static <T> T parseObject(String json, Class<T> clazz) {
        return JSON.parseObject(json, clazz);
    }

    /**
     * 将 JSON 字符串反序列化为 JSONObject
     *
     * @param json JSON 字符串
     * @return JSONObject 对象
     */
    public static JSONObject parseObject(String json) {
        return JSON.parseObject(json);
    }

    /**
     * 将对象序列化为 JSON 字符串
     *
     * @param object 待序列化对象
     * @return JSON 字符串
     */
    public static String toJSONString(Object object) {
        return JSON.toJSONString(object);
    }
}

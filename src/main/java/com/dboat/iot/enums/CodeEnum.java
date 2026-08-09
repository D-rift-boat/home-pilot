package com.dboat.iot.enums;

/**
 * 统一枚举接口 —— 所有业务枚举必须实现此接口
 * <p>
 * 提供统一的码值、中文名、英文名访问规范，
 * 便于序列化、前端展示、日志输出等场景统一处理。
 * </p>
 *
 * @author dboat
 */
public interface CodeEnum {

    /**
     * 获取枚举码值（数字编码，用于数据库存储和接口传输）
     *
     * @return 枚举对应的整数码值
     */
    int getCode();

    /**
     * 获取中文名称（用于日志输出、页面展示）
     *
     * @return 枚举的中文描述
     */
    String getNameCn();

    /**
     * 获取英文名称（用于国际化、英文日志）
     *
     * @return 枚举的英文描述
     */
    String getNameEn();
}

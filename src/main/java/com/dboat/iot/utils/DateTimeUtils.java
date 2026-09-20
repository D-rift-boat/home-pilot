package com.dboat.iot.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 日期时间工具类
 * <p>
 * 统一时区：Asia/Shanghai (GMT+8)
 * 默认格式：yyyy-MM-dd HH:mm:ss
 * 全部基于 java.time 包，线程安全
 * 命名规则：源类型To目标类型
 * </p>
 *
 * @author dboat
 */
public final class DateTimeUtils {

    private DateTimeUtils() {
        throw new UnsupportedOperationException("DateTimeUtils is a utility class, cannot be instantiated");
    }

    // ==================== 时区常量 ====================
    public static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");
    public static final ZoneId ZONE_UTC = ZoneId.of("UTC");

    /**
     * 硬件上报UTC毫秒时间戳 --> MySQL的LocalDateTime(北京时间，用于入库datetime)
     */
    public static LocalDateTime utcMilliToBeijingLocal(long utcMilli){
        return Instant.ofEpochMilli(utcMilli)
                .atZone(ZONE_UTC)
                .withZoneSameInstant(ZONE_SHANGHAI) //同一个物理时刻，转为东八区
                .toLocalDateTime();
    }

    /**
     * LocalDateTime(北京时间数据库读出) → UTC毫秒（写入Redis /下发消息）
     */
    public static long beijingLocalToUtcMilli(LocalDateTime beijingTime){
        return beijingTime.atZone(ZONE_SHANGHAI)
                .toInstant()
                .toEpochMilli();
    }

    // ==================== 日期格式常量 ====================
    public static final String PATTERN_DATETIME = "yyyy-MM-dd HH:mm:ss";
    public static final String PATTERN_DATE = "yyyy-MM-dd";
    public static final String PATTERN_DATETIME_COMPACT = "yyyyMMddHHmmss";
    public static final String PATTERN_ISO_DATETIME = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String PATTERN_ISO_INSTANT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    public static final String PATTERN_TIME = "HH:mm:ss";

    // ==================== DateTimeFormatter 常量（线程安全） ====================
    public static final DateTimeFormatter FORMATTER_DATETIME =
            DateTimeFormatter.ofPattern(PATTERN_DATETIME).withZone(ZONE_SHANGHAI);
    public static final DateTimeFormatter FORMATTER_DATE =
            DateTimeFormatter.ofPattern(PATTERN_DATE).withZone(ZONE_SHANGHAI);
    public static final DateTimeFormatter FORMATTER_DATETIME_COMPACT =
            DateTimeFormatter.ofPattern(PATTERN_DATETIME_COMPACT).withZone(ZONE_SHANGHAI);
    public static final DateTimeFormatter FORMATTER_ISO_DATETIME =
            DateTimeFormatter.ofPattern(PATTERN_ISO_DATETIME).withZone(ZONE_SHANGHAI);

    // ==================== String → 各种时间类型 ====================

    /** String → LocalDateTime（默认格式 yyyy-MM-dd HH:mm:ss） */
    public static LocalDateTime stringToLocalDateTime(String str) {
        return stringToLocalDateTime(str, PATTERN_DATETIME);
    }

    /** String → LocalDateTime（自定义格式） */
    public static LocalDateTime stringToLocalDateTime(String str, String pattern) {
        if (str == null || str.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(str, DateTimeFormatter.ofPattern(pattern));
    }

    /** String → Date（默认格式 yyyy-MM-dd HH:mm:ss） */
    public static Date stringToDate(String str) {
        return stringToDate(str, PATTERN_DATETIME);
    }

    /** String → Date（自定义格式） */
    public static Date stringToDate(String str, String pattern) {
        LocalDateTime localDateTime = stringToLocalDateTime(str, pattern);
        return localDateTime == null ? null : localDateTimeToDate(localDateTime);
    }

    /** String → Instant（默认格式 yyyy-MM-dd HH:mm:ss，按 GMT+8 解析） */
    public static Instant stringToInstant(String str) {
        return stringToInstant(str, PATTERN_DATETIME);
    }

    /** String → Instant（自定义格式，按 GMT+8 解析） */
    public static Instant stringToInstant(String str, String pattern) {
        LocalDateTime localDateTime = stringToLocalDateTime(str, pattern);
        return localDateTime == null ? null : localDateTimeToInstant(localDateTime);
    }

    // ==================== 毫秒时间戳 → 各种时间类型 ====================

    /** 毫秒时间戳 → LocalDateTime */
    public static LocalDateTime milliToLocalDateTime(long timestamp) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZONE_SHANGHAI);
    }

    /** 毫秒时间戳 → Instant */
    public static Instant milliToInstant(long timestamp) {
        return Instant.ofEpochMilli(timestamp);
    }

    /** 毫秒时间戳 → String（默认格式 yyyy-MM-dd HH:mm:ss） */
    public static String milliToString(long timestamp) {
        return localDateTimeToString(milliToLocalDateTime(timestamp));
    }

    // ==================== 各种时间类型 → String ====================

    /** LocalDateTime → String（默认格式 yyyy-MM-dd HH:mm:ss） */
    public static String localDateTimeToString(LocalDateTime dateTime) {
        return localDateTimeToString(dateTime, PATTERN_DATETIME);
    }

    /** LocalDateTime → String（自定义格式） */
    public static String localDateTimeToString(LocalDateTime dateTime, String pattern) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DateTimeFormatter.ofPattern(pattern));
    }

    /** Date → String（默认格式 yyyy-MM-dd HH:mm:ss） */
    public static String dateToString(Date date) {
        return dateToString(date, PATTERN_DATETIME);
    }

    /** Date → String（自定义格式） */
    public static String dateToString(Date date, String pattern) {
        if (date == null) {
            return null;
        }
        return dateToLocalDateTime(date).format(DateTimeFormatter.ofPattern(pattern));
    }

    /** Instant → String（默认格式 yyyy-MM-dd HH:mm:ss，转 GMT+8） */
    public static String instantToString(Instant instant) {
        return instantToString(instant, PATTERN_DATETIME);
    }

    /** Instant → String（自定义格式，转 GMT+8） */
    public static String instantToString(Instant instant, String pattern) {
        if (instant == null) {
            return null;
        }
        return DateTimeFormatter.ofPattern(pattern)
                .withZone(ZONE_SHANGHAI)
                .format(instant);
    }

    // ==================== LocalDateTime ↔ Instant 互转 ====================

    /** LocalDateTime → Instant（按 GMT+8 时区转换） */
    public static Instant localDateTimeToInstant(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atZone(ZONE_SHANGHAI).toInstant();
    }

    /** Instant → LocalDateTime（按 GMT+8 时区转换） */
    public static LocalDateTime instantToLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, ZONE_SHANGHAI);
    }

    // ==================== LocalDateTime ↔ Date 互转 ====================

    /** LocalDateTime → Date */
    public static Date localDateTimeToDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return Date.from(dateTime.atZone(ZONE_SHANGHAI).toInstant());
    }

    /** Date → LocalDateTime */
    public static LocalDateTime dateToLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZONE_SHANGHAI);
    }

    // ==================== Date ↔ Instant 互转 ====================

    /** Date → Instant */
    public static Instant dateToInstant(Date date) {
        return date == null ? null : date.toInstant();
    }

    /** Instant → Date */
    public static Date instantToDate(Instant instant) {
        return instant == null ? null : Date.from(instant);
    }

    // ==================== 便捷方法 ====================

    /** 获取当前时间的 LocalDateTime */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE_SHANGHAI);
    }

    /** 获取当前时间字符串（yyyy-MM-dd HH:mm:ss） */
    public static String nowStr() {
        return localDateTimeToString(now());
    }

    /** 获取当前毫秒时间戳 */
    public static long nowMilli() {
        return System.currentTimeMillis();
    }

    /** 获取当天开始时间（00:00:00） */
    public static LocalDateTime startOfDay() {
        return LocalDate.now(ZONE_SHANGHAI).atStartOfDay();
    }

    /** 获取当天结束时间（23:59:59） */
    public static LocalDateTime endOfDay() {
        return LocalDate.now(ZONE_SHANGHAI).atTime(23, 59, 59);
    }
}

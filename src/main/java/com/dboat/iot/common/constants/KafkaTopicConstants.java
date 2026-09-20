package com.dboat.iot.common.constants;

/**
 * Kafka Topic configuration key constant class
 * <p>
 * Only store the config keys of kafka topic in spring configuration file,
 * NOT the actual topic name.
 * Reference in annotation by ${constant}, avoid hard-coded magic string,
 * convenient for unified maintenance and refactor search.
 * </p>
 *
 * @author dboat
 * @date 2026-09-12
 */
public final class KafkaTopicConstants {

    /**
     * Private constructor, prevent instantiation of constant class
     */
    private KafkaTopicConstants() {
        throw new AssertionError("Constant class cannot be instantiated.");
    }

    /**
     * Config key for telemetry data topic.
     * Corresponding yml config: kafka.topic.telemetry-data
     */
    public static final String TELEMETRY_DATA_TOPIC = "kafka.topic.telemetry-data";

    /**
     * Config key for iot device status topic.
     * Corresponding yml config: kafka.topic.iot-device-conn
     */
    public static final String IOT_DEVICE_CONN_TOPIC = "kafka.topic.iot-device-conn";

    /**
     * Config key for iot device heartbeat topic.
     * Corresponding yml config: kafka.topic.iot-heartbeat
     */
    public static final String IOT_HEARTBEAT_TOPIC = "kafka.topic.iot-heartbeat";

    /**
     * Config key for kafka dead letter topic(DLT, dead letter queue).
     * Corresponding yml config: kafka.topic.dlq
     * Store messages that failed after multiple retries, for troubleshooting.
     */
    public static final String DLQ_TOPIC = "kafka.topic.dlq";
}

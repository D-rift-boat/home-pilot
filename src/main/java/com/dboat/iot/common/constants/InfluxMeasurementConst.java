package com.dboat.iot.common.constants;

/**
 * InfluxDB measurement 常量
 */
public final class InfluxMeasurementConst {
	/**
	 * 原始上报数据 measurement
	 */
	public static final String RAW_SENSOR_TELEMETRY = "sensor_telemetry";
	/**
	 * 5分钟聚合 measurement
	 */
	public static final String AGG_ENV_METRIC_5MIN = "env_metric_5min";
	/**
	 * 1小时聚合 measurement
	 */
	public static final String AGG_ENV_METRIC_1H = "env_metric_1h";
	/**
	 * 1天聚合 measurement
	 */
	public static final String AGG_ENV_METRIC_1D = "env_metric_1d";
}

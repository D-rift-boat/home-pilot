package com.dboat.iot.device;

import com.influxdb.client.BucketsApi;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.domain.Bucket;
import com.influxdb.client.domain.Label;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class HomePilotApplicationTests {

    @Resource
    private InfluxDBClient influxDBClient;

    @Test
    void contextLoads() {

        BucketsApi bucketsApi = influxDBClient.getBucketsApi();

        // 1. 根据bucket名字获取bucket实例
        Bucket bucket = bucketsApi.findBucketByName("sensor");
        if(bucket == null){
            System.out.println("bucket不存在");
            return;
        }
        List<Label> bucketsApiLabels = bucketsApi.getLabels(bucket.getId());
        System.out.println(bucketsApiLabels);

    }
}

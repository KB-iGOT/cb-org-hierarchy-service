package com.igot.cb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ApplicationConfiguration {

    @Value("${neo4j.maxlevel}")
    private int maxLevel;

    @Value("${redis.data.host.name}")
    private String redisDataHostName;

    @Value("${redis.data.port}")
    private String redisDataPort;

    @Value("${redis.host.name}")
    private String redisHostName;

    @Value("${redis.port}")
    private String redisPort;

    public String getRedisTimeout() {
        return redisTimeout;
    }

    public void setRedisTimeout(String redisTimeout) {
        this.redisTimeout = redisTimeout;
    }

    @Value("${redis.timeout}")
    private String redisTimeout;

    public int getMaxLevel() {
        return maxLevel;
    }

    public void setMaxLevel(int maxLevel) {
        this.maxLevel = maxLevel;
    }

    public String getRedisDataHostName() {
        return redisDataHostName;
    }

    public void setRedisDataHostName(String redisDataHostName) {
        this.redisDataHostName = redisDataHostName;
    }

    public String getRedisDataPort() {
        return redisDataPort;
    }

    public void setRedisDataPort(String redisDataPort) {
        this.redisDataPort = redisDataPort;
    }

    public String getRedisHostName() {
        return redisHostName;
    }

    public void setRedisHostName(String redisHostName) {
        this.redisHostName = redisHostName;
    }

    public String getRedisPort() {
        return redisPort;
    }

    public void setRedisPort(String redisPort) {
        this.redisPort = redisPort;
    }
}

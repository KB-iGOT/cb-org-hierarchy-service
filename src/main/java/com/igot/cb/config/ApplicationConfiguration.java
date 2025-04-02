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

    @Value("${neo4j.uri}")
    private String neo4jUri;

    @Value("${neo4j.username}")
    private String neo4jUsername;

    @Value("${neo4j.password}")
    private String neo4jPassword;

    @Value("${redis.timeout}")
    private String redisTimeout;

    @Value("${neo4j.auth.enable}")
    private String neo4jAuthEnable;

    @Value("${neo.timeout}")
    private Long neoTimeout;

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

    public String getNeo4jUri() {
        return neo4jUri;
    }

    public void setNeo4jUri(String neo4jUri) {
        this.neo4jUri = neo4jUri;
    }

    public String getNeo4jUsername() {
        return neo4jUsername;
    }

    public void setNeo4jUsername(String neo4jUsername) {
        this.neo4jUsername = neo4jUsername;
    }

    public String getNeo4jPassword() {
        return neo4jPassword;
    }

    public void setNeo4jPassword(String neo4jPassword) {
        this.neo4jPassword = neo4jPassword;
    }

    public String getRedisTimeout() {
        return redisTimeout;
    }

    public String getNeo4jAuthEnable() {
        return neo4jAuthEnable;
    }

    public Long getNeoTimeout() {
        return neoTimeout;
    }
}

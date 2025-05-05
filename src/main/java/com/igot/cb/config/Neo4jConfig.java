package com.igot.cb.config;

import com.igot.cb.exception.GraphException;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Config;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.exceptions.AuthenticationException;
import org.neo4j.driver.v1.exceptions.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

@Configuration
public class Neo4jConfig {

    private static final Logger logger = LoggerFactory.getLogger(Neo4jConfig.class);
    private final PropertiesCache propertiesCache = PropertiesCache.getInstance();
    private Driver driver;

    @Bean
    public Driver neo4jDriver() {
        try {
            String uri = propertiesCache.getProperty("neo4j.uri");
            String username = propertiesCache.getProperty("neo4j.username");
            String password = propertiesCache.getProperty("neo4j.password");
            String authEnabledStr = propertiesCache.getProperty("neo4j.auth.enable");
            String timeoutStr = propertiesCache.getProperty("neo.timeout");

            logger.info("Neo4j URI: {}", uri);
            logger.info("Auth Enabled: {}", authEnabledStr);
            logger.info("Timeout: {}", timeoutStr);

            boolean authEnabled = Boolean.parseBoolean(authEnabledStr);
            int timeout = timeoutStr != null ? Integer.parseInt(timeoutStr) : 30;

            if (authEnabled) {
                driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));
                logger.info("Neo4j driver initialized with authentication.");
            } else {
                Config config = Config.builder()
                        .withConnectionTimeout(timeout, TimeUnit.SECONDS)
                        .withConnectionLivenessCheckTimeout(10, TimeUnit.SECONDS)
                        .build();
                driver = GraphDatabase.driver(uri, config);
                logger.info("Neo4j driver initialized without authentication using timeout config.");
            }
            return driver;

        } catch (AuthenticationException | ServiceUnavailableException e) {
            logger.error("Neo4j connection error: ", e);
            throw new GraphException(e.code(), e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error initializing Neo4j driver: ", e);
            throw new RuntimeException("Unexpected error in Neo4j configuration", e);
        }
    }

    @PreDestroy
    public void closeDriver() {
        if (driver != null) {
            logger.info("Closing Neo4j driver");
            driver.close();
        }
    }
}

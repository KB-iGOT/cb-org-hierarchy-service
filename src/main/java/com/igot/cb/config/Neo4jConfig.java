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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class Neo4jConfig {


    @Autowired ApplicationConfiguration configuration;

    private Logger logger = LoggerFactory.getLogger(Neo4jConfig.class);

    @Bean
    public Driver Neo4jDriver() {

        try {
            if (Boolean.parseBoolean(configuration.getNeo4jAuthEnable())) {
                return GraphDatabase.driver(configuration.getNeo4jUri(),
                        AuthTokens.basic(configuration.getNeo4jUsername(), configuration.getNeo4jPassword()));
            } else {
                Config config = Config.build()
                        .withConnectionTimeout(configuration.getNeoTimeout(), TimeUnit.SECONDS)
                        .withConnectionLivenessCheckTimeout(10L, TimeUnit.SECONDS).toConfig();
                logger.info("Using timeout config of : " + configuration.getNeoTimeout().toString());
                return GraphDatabase.driver(configuration.getNeo4jUri(), config);
            }

        } catch (AuthenticationException | ServiceUnavailableException e) {
            throw new GraphException(e.code(), e.getMessage());
        }

    }
}

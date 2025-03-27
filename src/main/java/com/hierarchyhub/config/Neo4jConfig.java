package com.hierarchyhub.config;

import org.neo4j.ogm.session.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

@Configuration
@EnableNeo4jRepositories(basePackages = "com.hierarchyhub.repository")
public class Neo4jConfig {


    @Autowired ApplicationConfiguration configuration;

    @Bean
    public org.neo4j.ogm.config.Configuration configuration() {
        return new org.neo4j.ogm.config.Configuration.Builder()
                .uri(configuration.getNeo4jUri())
                .credentials(configuration.getNeo4jUsername(), configuration.getNeo4jPassword())
                .build();
    }

    @Bean
    public SessionFactory sessionFactory() {
        return new SessionFactory(configuration(), "com.hierarchyhub.model");
    }


}

package com.hierarchyhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {
		org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration.class,
		org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class
})
public class HierarchyHubApplication {

	public static void main(String[] args) {
		SpringApplication.run(HierarchyHubApplication.class, args);
	}

}

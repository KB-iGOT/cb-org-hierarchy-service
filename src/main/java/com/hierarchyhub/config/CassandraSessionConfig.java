package com.hierarchyhub.config;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;
import java.util.Arrays;

/**
 * The CQL session used to read the Sunbird organisation master (name, status, ministry). Created only when
 * {@code org-hierarchy.cassandra.enabled=true} (the default); when it is off, {@link com.hierarchyhub.repository
 * .NoopOrganisationMasterRepository} is used instead and no connection is attempted.
 */
@Configuration
@ConditionalOnProperty(prefix = "org-hierarchy.cassandra", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CassandraSessionConfig {

    @Bean(destroyMethod = "close")
    public CqlSession organisationMasterSession(
            @Value("${org-hierarchy.cassandra.contact-points:localhost:9042}") String contactPoints,
            @Value("${org-hierarchy.cassandra.local-datacenter:datacenter1}") String localDatacenter,
            OrgHierarchyProperties props) {

        CqlSessionBuilder builder = CqlSession.builder()
                .withLocalDatacenter(localDatacenter)
                .withKeyspace(props.getCassandra().getKeyspace());

        Arrays.stream(contactPoints.split(","))
                .map(String::trim)
                .filter(hostPort -> !hostPort.isEmpty())
                .forEach(hostPort -> builder.addContactPoint(toSocketAddress(hostPort)));

        return builder.build();
    }

    private static InetSocketAddress toSocketAddress(String hostPort) {
        String[] parts = hostPort.split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9042;
        return InetSocketAddress.createUnresolved(host, port);
    }
}

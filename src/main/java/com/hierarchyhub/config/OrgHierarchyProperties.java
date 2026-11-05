package com.hierarchyhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Tunables for the org-hierarchy v2 module (link / move / unlink), all under the {@code org-hierarchy} prefix.
 */
@Configuration
@ConfigurationProperties(prefix = "org-hierarchy")
public class OrgHierarchyProperties {

    /** Deepest parent chain walked by cycle checks and ancestor lookups. */
    private int maxDepth = 50;

    private final Ministry ministry = new Ministry();
    private final Cassandra cassandra = new Cassandra();
    private final Bulk bulk = new Bulk();

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public Ministry getMinistry() {
        return ministry;
    }

    public Cassandra getCassandra() {
        return cassandra;
    }

    public Bulk getBulk() {
        return bulk;
    }

    /** Whether link/move must keep the org and its new parent inside the same ministry. */
    public static class Ministry {

        private boolean checkEnabled = true;

        /** How a missing ministry value is treated: {@code BLOCK} refuses the change, {@code ALLOW} treats it as a match. */
        private MissingPolicy missing = MissingPolicy.BLOCK;

        public boolean isCheckEnabled() {
            return checkEnabled;
        }

        public void setCheckEnabled(boolean checkEnabled) {
            this.checkEnabled = checkEnabled;
        }

        public MissingPolicy getMissing() {
            return missing;
        }

        public void setMissing(MissingPolicy missing) {
            this.missing = missing;
        }

        public enum MissingPolicy {
            BLOCK, ALLOW
        }
    }

    /** {@code POST /bulk/link}: the uploaded sheet is read in memory, then every row is attached by a small background thread pool. */
    public static class Bulk {

        /** A file with more data rows than this is refused outright (400) rather than queued. */
        private int maxRows = 5000;

        private int corePoolSize = 2;
        private int maxPoolSize = 4;
        private int queueCapacity = 100;

        public int getMaxRows() {
            return maxRows;
        }

        public void setMaxRows(int maxRows) {
            this.maxRows = maxRows;
        }

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }
    }

    /**
     * The Sunbird organisation master table (Cassandra): name, status and ministry/state of every org. Read only.
     * Only what truly varies per environment is here - the keyspace and whether the master is reachable at all; the
     * table name and column names are a fixed schema fact, not environment config, so {@link
     * com.hierarchyhub.repository.CassandraOrganisationMasterRepository} holds those as constants instead.
     */
    public static class Cassandra {

        private boolean enabled = true;
        private String keyspace = "sunbird";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getKeyspace() {
            return keyspace;
        }

        public void setKeyspace(String keyspace) {
            this.keyspace = keyspace;
        }
    }
}

package com.hierarchyhub.repository;

import com.hierarchyhub.model.OrganisationRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.Collections;
import java.util.Optional;

/**
 * Used when {@code org-hierarchy.cassandra.enabled=false} (local/test runs without a Cassandra master). The ministry
 * check is skipped in this mode; a new org can still be linked, but only if its row already exists in {@code org_hierarchy}
 * (nothing can fetch its name from a master that is turned off).
 */
@Repository
@ConditionalOnProperty(prefix = "org-hierarchy.cassandra", name = "enabled", havingValue = "false")
public class NoopOrganisationMasterRepository implements OrganisationMasterRepository {

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public Optional<OrganisationRecord> find(String id) {
        return Optional.empty();
    }

    @Override
    public Map<String, OrganisationRecord> findAll(Collection<String> ids) {
        return Collections.emptyMap();
    }
}

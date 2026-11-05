package com.hierarchyhub.repository;

import com.hierarchyhub.model.OrganisationRecord;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only access to the Sunbird organisation master (name, status, ministry). The hierarchy never writes this store.
 */
public interface OrganisationMasterRepository {

    /** False when the master is disabled (local/test runs); callers then skip the ministry check entirely. */
    boolean enabled();

    Optional<OrganisationRecord> find(String id);

    /** The records that exist among {@code ids}, keyed by id; an id missing from the map is not in the master at all. */
    Map<String, OrganisationRecord> findAll(Collection<String> ids);
}

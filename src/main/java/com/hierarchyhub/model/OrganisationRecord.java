package com.hierarchyhub.model;

/**
 * The organisation-master fields read from Cassandra for an org: its name, whether it is active, and the ministry/state
 * it belongs to. The hierarchy never stores these itself; they are read fresh from the master before every link and move.
 */
public final class OrganisationRecord {

    private final String id;
    private final String name;
    private final boolean active;
    private final String ministryId;

    public OrganisationRecord(String id, String name, boolean active, String ministryId) {
        this.id = id;
        this.name = name;
        this.active = active;
        this.ministryId = ministryId;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public boolean active() {
        return active;
    }

    public String ministryId() {
        return ministryId;
    }
}

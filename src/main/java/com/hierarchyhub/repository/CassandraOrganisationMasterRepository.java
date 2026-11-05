package com.hierarchyhub.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.hierarchyhub.config.OrgHierarchyProperties;
import com.hierarchyhub.model.OrganisationRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Reads the Sunbird {@code organisation} table in Cassandra. Table/column names are a fixed schema fact (see the constants below); only the keyspace is environment config (see {@link OrgHierarchyProperties.Cassandra}). */
@Repository
@ConditionalOnProperty(prefix = "org-hierarchy.cassandra", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CassandraOrganisationMasterRepository implements OrganisationMasterRepository {

    private static final String TABLE = "organisation";
    private static final String ID_COLUMN = "id";
    private static final String NAME_COLUMN = "orgname";
    private static final String STATUS_COLUMN = "status";
    private static final String MINISTRY_COLUMN = "ministryorstateid";
    private static final int ACTIVE_STATUS = 1;

    private final CqlSession session;
    private final PreparedStatement selectOne;

    public CassandraOrganisationMasterRepository(CqlSession session, OrgHierarchyProperties props) {
        this.session = session;
        String cql = "SELECT " + ID_COLUMN + ", " + NAME_COLUMN + ", " + STATUS_COLUMN + ", " + MINISTRY_COLUMN
                + " FROM " + props.getCassandra().getKeyspace() + "." + TABLE + " WHERE " + ID_COLUMN + " = ?";
        this.selectOne = session.prepare(cql);
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Optional<OrganisationRecord> find(String id) {
        BoundStatement bound = selectOne.bind(id);
        Row row = session.execute(bound).one();
        return row == null ? Optional.empty() : Optional.of(toRecord(row));
    }

    @Override
    public Map<String, OrganisationRecord> findAll(Collection<String> ids) {
        Map<String, OrganisationRecord> out = new LinkedHashMap<>();
        Set<String> unique = new LinkedHashSet<>(ids);
        for (String id : unique) {
            find(id).ifPresent(record -> out.put(record.id(), record));
        }
        return out;
    }

    private static OrganisationRecord toRecord(Row row) {
        String id = row.getString(ID_COLUMN);
        String name = row.getString(NAME_COLUMN);
        Integer status = row.isNull(STATUS_COLUMN) ? null : row.getInt(STATUS_COLUMN);
        String ministryId = row.getString(MINISTRY_COLUMN);
        boolean active = status != null && status == ACTIVE_STATUS;
        return new OrganisationRecord(id, name, active, ministryId);
    }
}

package com.hierarchyhub.validation;

import com.hierarchyhub.config.OrgHierarchyProperties;
import com.hierarchyhub.exception.ErrorCode;
import com.hierarchyhub.exception.ServiceException;
import com.hierarchyhub.model.OrganisationRecord;
import com.hierarchyhub.repository.OrganisationMasterRepository;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class OrgHierarchyValidatorTest {

    private final FakeOrganisationMasterRepository master = new FakeOrganisationMasterRepository();
    private final OrgHierarchyProperties props = new OrgHierarchyProperties();
    private final OrgHierarchyValidator validator = new OrgHierarchyValidator(master, props);

    @Before
    public void setUp() {
        master.records.clear();
        master.enabled = true;
        props.getMinistry().setCheckEnabled(true);
        props.getMinistry().setMissing(OrgHierarchyProperties.Ministry.MissingPolicy.BLOCK);
    }

    // ---- requireDistinct (shared by /link and /move) ----

    @Test
    public void requireDistinctAcceptsTwoDifferentIds() {
        validator.requireDistinct("child-1", "parent-1");
    }

    @Test
    public void requireDistinctRefusesAnOrgAsItsOwnParent() {
        try {
            validator.requireDistinct("org-1", "org-1");
            fail("Expected ServiceException");
        } catch (ServiceException e) {
            assertEquals(ErrorCode.CYCLE_DETECTED, e.errorCode());
        }
    }

    // ---- checkMinistryOrStateCompliance ----

    @Test
    public void allowsLinkingWithinTheSameMinistry() {
        master.put("child-1", "Child", true, "ministry-1");
        master.put("parent-1", "Parent", true, "ministry-1");

        Map<String, OrganisationRecord> result = validator.checkMinistryOrStateCompliance("child-1", "parent-1");

        assertEquals(2, result.size());
    }

    @Test
    public void refusesAcrossMinistries() {
        master.put("child-1", "Child", true, "ministry-1");
        master.put("parent-1", "Parent", true, "ministry-2");

        try {
            validator.checkMinistryOrStateCompliance("child-1", "parent-1");
            fail("Expected ServiceException");
        } catch (ServiceException e) {
            assertEquals(ErrorCode.MINISTRY_MISMATCH, e.errorCode());
        }
    }

    @Test
    public void refusesWhenOrgIsMissingFromTheMaster() {
        master.put("parent-1", "Parent", true, "ministry-1");

        try {
            validator.checkMinistryOrStateCompliance("child-missing", "parent-1");
            fail("Expected ServiceException");
        } catch (ServiceException e) {
            assertEquals(ErrorCode.ORG_NOT_FOUND, e.errorCode());
        }
    }

    @Test
    public void refusesAnInactiveOrg() {
        master.put("child-1", "Child", false, "ministry-1");
        master.put("parent-1", "Parent", true, "ministry-1");

        try {
            validator.checkMinistryOrStateCompliance("child-1", "parent-1");
            fail("Expected ServiceException");
        } catch (ServiceException e) {
            assertEquals(ErrorCode.ORG_INACTIVE, e.errorCode());
        }
    }

    @Test
    public void allowsAMissingMinistryWhenThePolicyIsAllow() {
        props.getMinistry().setMissing(OrgHierarchyProperties.Ministry.MissingPolicy.ALLOW);
        master.put("child-1", "Child", true, null);
        master.put("parent-1", "Parent", true, "ministry-1");

        validator.checkMinistryOrStateCompliance("child-1", "parent-1");
    }

    @Test
    public void skipsTheCheckWhenTheMasterIsDisabled() {
        master.enabled = false;

        Map<String, OrganisationRecord> result = validator.checkMinistryOrStateCompliance("child-1", "parent-1");

        assertEquals(0, result.size());
    }

    /** A tiny in-memory stand-in for the Cassandra-backed repository, so these tests need no database. */
    private static final class FakeOrganisationMasterRepository implements OrganisationMasterRepository {

        private final Map<String, OrganisationRecord> records = new LinkedHashMap<>();
        private boolean enabled = true;

        void put(String id, String name, boolean active, String ministryId) {
            records.put(id, new OrganisationRecord(id, name, active, ministryId));
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public Optional<OrganisationRecord> find(String id) {
            return Optional.ofNullable(records.get(id));
        }

        @Override
        public Map<String, OrganisationRecord> findAll(Collection<String> ids) {
            Map<String, OrganisationRecord> out = new LinkedHashMap<>();
            for (String id : ids) {
                if (records.containsKey(id)) {
                    out.put(id, records.get(id));
                }
            }
            return out;
        }
    }
}

package com.hierarchyhub.validation;

import com.hierarchyhub.config.OrgHierarchyProperties;
import com.hierarchyhub.exception.ErrorCode;
import com.hierarchyhub.exception.ServiceException;
import com.hierarchyhub.model.OrganisationRecord;
import com.hierarchyhub.repository.OrganisationMasterRepository;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Every validation rule for the org-hierarchy v2 APIs, in one place: request-shape checks that need no database read,
 * and the ministry check against the organisation master. {@link com.hierarchyhub.service.impl
 * .OrgHierarchyServiceImpl} calls whichever method applies to the request it is handling.
 */
@Component
public class OrgHierarchyValidator {

    private final OrganisationMasterRepository master;
    private final OrgHierarchyProperties props;

    public OrgHierarchyValidator(OrganisationMasterRepository master, OrgHierarchyProperties props) {
        this.master = master;
        this.props = props;
    }

    /** An org cannot be linked or moved under itself. Used for both {@code /link} and {@code /move}. */
    public void requireDistinct(String orgId, String parentId) {
        if (orgId.equals(parentId)) {
            throw new ServiceException(ErrorCode.CYCLE_DETECTED, "An org cannot be its own parent");
        }
    }

    /**
     * Reads {@code childId} and {@code parentId} from the organisation master in one batch, confirms both exist and are
     * active, and - when the ministry check is enabled - that they share a ministry. Skipped entirely when the master is
     * disabled. Returns the two records (the caller may need the child's name to create its hierarchy row).
     */
    public Map<String, OrganisationRecord> checkMinistryOrStateCompliance(String childId, String parentId) {
        if (!master.enabled()) {
            return Collections.emptyMap();
        }
        List<String> ids = Arrays.asList(childId, parentId);
        Map<String, OrganisationRecord> found = master.findAll(ids);
        for (String id : ids) {
            if (!found.containsKey(id)) {
                throw new ServiceException(ErrorCode.ORG_NOT_FOUND,
                        "Org " + id + " not found in the organisation master");
            }
            if (!found.get(id).active()) {
                throw new ServiceException(ErrorCode.ORG_INACTIVE, "Org " + id + " is inactive");
            }
        }
        if (props.getMinistry().isCheckEnabled()) {
            requireSameMinistryOrState(found.get(childId), found.get(parentId));
        }
        return found;
    }

    private void requireSameMinistryOrState(OrganisationRecord child, OrganisationRecord parent) {
        String childMinistry = child.ministryId();
        String parentMinistry = parent.ministryId();
        if (childMinistry == null || parentMinistry == null) {
            if (props.getMinistry().getMissing() == OrgHierarchyProperties.Ministry.MissingPolicy.ALLOW) {
                return;
            }
            String who = childMinistry == null && parentMinistry == null
                    ? "Neither " + child.id() + " nor " + parent.id() + " has"
                    : (childMinistry == null ? child.id() : parent.id()) + " has no";
            throw new ServiceException(ErrorCode.MINISTRY_MISMATCH, who + " ministry in the organisation master");
        }
        if (!Objects.equals(childMinistry, parentMinistry)) {
            throw new ServiceException(ErrorCode.MINISTRY_MISMATCH,
                    "Org " + child.id() + " belongs to ministry " + childMinistry + " but the parent " + parent.id()
                            + " belongs to ministry " + parentMinistry);
        }
    }
}

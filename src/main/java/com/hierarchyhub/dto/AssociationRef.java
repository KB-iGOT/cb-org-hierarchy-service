package com.hierarchyhub.dto;

import javax.validation.constraints.NotBlank;

/** One child to attach, as sent in {@code request.associations[]}. */
public class AssociationRef {

    @NotBlank(message = "childOrgId is required")
    private String childOrgId;

    public AssociationRef() {
    }

    public AssociationRef(String childOrgId) {
        this.childOrgId = childOrgId;
    }

    public String getChildOrgId() {
        return childOrgId;
    }

    public void setChildOrgId(String childOrgId) {
        this.childOrgId = childOrgId;
    }
}

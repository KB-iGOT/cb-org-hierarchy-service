package com.hierarchyhub.dto;

import com.hierarchyhub.model.Organization;

import java.util.List;

public class OrganizationDTO {

    private Organization parent;
    private List<OrganizationDTO> children;

    public Organization getParent() {
        return parent;
    }

    public void setParent(Organization parent) {
        this.parent = parent;
    }

    public List<OrganizationDTO> getChildren() {
        return children;
    }

    public void setChildren(List<OrganizationDTO> children) {
        this.children = children;
    }
}

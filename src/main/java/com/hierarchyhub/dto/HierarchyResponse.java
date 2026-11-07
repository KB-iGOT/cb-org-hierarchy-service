package com.hierarchyhub.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HierarchyResponse {

    @JsonUnwrapped
    private final OrgTreeNode org;
    private final int count;
}

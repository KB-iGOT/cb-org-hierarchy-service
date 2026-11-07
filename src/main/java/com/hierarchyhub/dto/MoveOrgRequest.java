package com.hierarchyhub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MoveOrgRequest {

    @NotNull(message = "request is required")
    @Valid
    private MoveOrgRequestBody request;
}

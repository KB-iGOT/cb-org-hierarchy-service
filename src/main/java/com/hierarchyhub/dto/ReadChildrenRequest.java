package com.hierarchyhub.dto;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReadChildrenRequest {

    @Valid
    private ReadChildrenRequestBody request;
}

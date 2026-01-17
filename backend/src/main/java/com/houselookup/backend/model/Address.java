package com.houselookup.backend.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Address(
    String uprn,
    String line1,
    String line2,
    String town,
    String postcode
) {}

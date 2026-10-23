package com.aquaalert.dto.area;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AreaRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 100) String ward,
        @NotBlank @Size(max = 100) String zone,
        @Size(max = 150) String street
) {}

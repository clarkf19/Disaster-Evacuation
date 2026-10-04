package com.mumbai.evacuation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Chat message plus the user's optional location (used to suggest nearby open shelters). */
public record ChatRequest(
        @NotBlank @Size(max = 1000) String message,
        Double userLat,
        Double userLon) {
}

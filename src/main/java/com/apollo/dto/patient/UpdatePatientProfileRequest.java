package com.apollo.dto.patient;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body to update patient profile baseline attributes")
public record UpdatePatientProfileRequest(
        @Size(max = 20, message = "Gender must not exceed 20 characters")
        @Schema(description = "Gender identity (e.g. MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY)", example = "FEMALE")
        String gender,

        @Schema(description = "Height in centimeters", example = "172.5")
        Double heightCm,

        @Schema(description = "Weight in kilograms", example = "65.0")
        Double weightKg,

        @Size(max = 10, message = "Blood type must not exceed 10 characters")
        @Schema(description = "Blood type (e.g. A+, O-, B+)", example = "O+")
        String bloodType
) {
    public UpdatePatientProfileRequest(String gender, Double heightCm, Double weightKg) {
        this(gender, heightCm, weightKg, null);
    }
}

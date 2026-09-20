package com.apollo.dto.patient;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Request body to update patient profile baseline attributes")
public record UpdatePatientProfileRequest(
        @Size(max = 100, message = "First name must not exceed 100 characters")
        @Schema(description = "Patient first name", example = "Jane")
        String firstName,

        @Size(max = 100, message = "Last name must not exceed 100 characters")
        @Schema(description = "Patient last name", example = "Doe")
        String lastName,

        @Schema(description = "Patient date of birth", example = "1990-05-15")
        LocalDate dateOfBirth,

        @Email(message = "Invalid email format")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        @Schema(description = "Patient account email address", example = "jane.doe@example.com")
        String email,

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
        this(null, null, null, null, gender, heightCm, weightKg, null);
    }

    public UpdatePatientProfileRequest(String gender, Double heightCm, Double weightKg, String bloodType) {
        this(null, null, null, null, gender, heightCm, weightKg, bloodType);
    }
}

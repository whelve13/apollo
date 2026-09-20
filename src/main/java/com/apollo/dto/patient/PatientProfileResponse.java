package com.apollo.dto.patient;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Detailed patient profile response")
public class PatientProfileResponse {

    @Schema(description = "Patient profile unique identifier")
    private UUID id;

    @Schema(description = "Associated user unique identifier")
    private UUID userId;

    @Schema(description = "Associated user email address", example = "jane.doe@example.com")
    private String email;

    @Schema(description = "Patient first name", example = "Jane")
    private String firstName;

    @Schema(description = "Patient last name", example = "Doe")
    private String lastName;

    @Schema(description = "Date of birth", example = "1990-05-15")
    private LocalDate dateOfBirth;

    @Schema(description = "Blood type", example = "O+")
    private String bloodType;

    @Schema(description = "Gender identity", example = "FEMALE")
    private String gender;

    @Schema(description = "Height in centimeters", example = "172.5")
    private Double heightCm;

    @Schema(description = "Weight in kilograms", example = "65.0")
    private Double weightKg;

    @Schema(description = "Profile creation timestamp")
    private Instant createdAt;
}

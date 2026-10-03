package com.apollo.dto.auth;

import com.apollo.domain.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Details of the currently authenticated user")
public class CurrentUserResponse {

    @Schema(description = "User unique identifier")
    private UUID userId;

    @Schema(description = "Patient or Doctor profile unique identifier")
    private UUID profileId;

    @Schema(description = "User email address")
    private String email;

    @Schema(description = "User account role")
    private Role role;

    @Schema(description = "Full name of the user or doctor")
    private String fullName;

    @Schema(description = "Date of birth (patient profiles)", example = "1990-05-15")
    private java.time.LocalDate dateOfBirth;

    @Schema(description = "Blood type (patient profiles)", example = "O+")
    private String bloodType;

    @Schema(description = "Gender identity (patient profiles)", example = "FEMALE")
    private String gender;

    @Schema(description = "Height in centimeters (patient profiles)", example = "172.5")
    private Double heightCm;

    @Schema(description = "Weight in kilograms (patient profiles)", example = "65.0")
    private Double weightKg;

    @Schema(description = "Medical license number (doctor profiles)", example = "MD-12345678")
    private String licenseNumber;

    @Schema(description = "Medical specialty (doctor profiles)", example = "Cardiology")
    private String specialty;

    @Schema(description = "Doctor role category (doctor profiles)", example = "SPECIALIST")
    private com.apollo.domain.enums.DoctorRole doctorRole;

    @Schema(description = "Account creation timestamp")
    private Instant createdAt;
}

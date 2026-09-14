package com.apollo.dto.auth;

import com.apollo.domain.enums.DoctorRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request body for doctor account registration")
public class RegisterDoctorRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Schema(example = "doctor@hospital.org")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @Schema(example = "DoctorSecret123!")
    private String password;

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    @Schema(example = "Gregory")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    @Schema(example = "House")
    private String lastName;

    @NotBlank(message = "Medical license number is required")
    @Size(max = 50, message = "License number must not exceed 50 characters")
    @Schema(example = "MD-98765432")
    private String licenseNumber;

    @NotBlank(message = "Medical specialty is required")
    @Size(max = 100, message = "Specialty must not exceed 100 characters")
    @Schema(example = "Diagnostics & Nephrology")
    private String specialty;

    @Schema(description = "Specific clinician or healthcare worker role (defaults to GENERAL_PRACTITIONER)", example = "GENERAL_PRACTITIONER")
    private DoctorRole doctorRole;
}

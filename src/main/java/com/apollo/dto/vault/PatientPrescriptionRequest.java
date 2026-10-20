package com.apollo.dto.vault;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientPrescriptionRequest {

    @NotBlank(message = "Medication name is required")
    @Size(max = 200, message = "Medication name must not exceed 200 characters")
    private String medicationName;

    @Size(max = 100, message = "Dosage must not exceed 100 characters")
    private String dosage;

    @Size(max = 100, message = "Frequency must not exceed 100 characters")
    private String frequency;

    private String instructions;

    private Instant issuedAt;
}

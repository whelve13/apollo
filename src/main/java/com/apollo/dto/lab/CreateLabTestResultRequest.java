package com.apollo.dto.lab;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload for logging a numerical lab test result or biometric measurement")
public class CreateLabTestResultRequest {

    @NotNull(message = "Patient ID is required")
    @Schema(description = "UUID of the patient recipient", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID patientId;

    @NotBlank(message = "Test name is required")
    @Size(max = 150, message = "Test name must not exceed 150 characters")
    @Schema(description = "Name of the diagnostic test or biomarker", example = "Fasting Blood Glucose")
    private String testName;

    @NotNull(message = "Numeric value is required")
    @Schema(description = "Numerical result of the test", example = "95.5")
    private BigDecimal numericValue;

    @NotBlank(message = "Unit of measurement is required")
    @Size(max = 50, message = "Unit must not exceed 50 characters")
    @Schema(description = "Standard unit of measurement", example = "mg/dL")
    private String unit;

    @NotNull(message = "Recorded date and time is required")
    @PastOrPresent(message = "Recorded date cannot be in the future")
    @Schema(description = "Timestamp when the specimen was drawn or test was executed")
    private Instant recordedAt;
}

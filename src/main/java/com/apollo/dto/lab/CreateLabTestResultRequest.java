package com.apollo.dto.lab;

import com.apollo.domain.enums.LabResultStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload for logging a single lab test result or biometric measurement")
public class CreateLabTestResultRequest extends LabResultValueRequest {

    @NotNull(message = "Patient ID is required")
    @Schema(description = "UUID of the patient recipient", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID patientId;

    @NotNull(message = "Recorded date and time is required")
    @PastOrPresent(message = "Recorded date cannot be in the future")
    @Schema(description = "Timestamp when the specimen was drawn or test was executed")
    private Instant recordedAt;

    @Schema(description = "DRAFT results are visible only to the doctor who wrote them. Defaults to FINAL.", defaultValue = "FINAL")
    private LabResultStatus status;
}

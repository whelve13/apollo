package com.apollo.dto.prescription;

import com.apollo.domain.enums.PrescriptionStatus;
import com.apollo.domain.enums.SourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Prescription details for pharmacy dispensing and clinical audit")
public class PrescriptionResponse {

    @Schema(description = "Prescription unique identifier", example = "c2eebc99-9c0b-4ef8-bb6d-6bb9bd380a33")
    private UUID id;

    @Schema(description = "Patient profile UUID", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID patientId;

    @Schema(description = "Prescribing doctor profile UUID", example = "d3eebc99-9c0b-4ef8-bb6d-6bb9bd380a44")
    private UUID doctorId;

    @Schema(description = "Prescribing doctor's full name", example = "Dr. Gregory House")
    private String doctorName;

    @Schema(description = "Optional linked clinical encounter UUID", example = "b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22")
    private UUID encounterId;

    @Schema(description = "Name and strength of prescribed medication", example = "Amoxicillin 500mg")
    private String medicationName;

    @Schema(description = "Prescribed dosage instructions", example = "1 capsule 3 times daily")
    private String dosage;

    @Schema(description = "Prescribed frequency instructions", example = "Once daily with breakfast")
    private String frequency;

    @Schema(description = "Dispensing instructions and patient directions", example = "Take with food for 7 days. Complete full course.")
    private String instructions;

    @Schema(description = "Current prescription lifecycle status", example = "ACTIVE")
    private PrescriptionStatus status;

    @Schema(description = "Source of the prescription record (DOCTOR_VERIFIED or PATIENT_DECLARED)")
    private SourceType source;

    @Schema(description = "Timestamp when the prescription was issued")
    private Instant issuedAt;

    @Schema(description = "Timestamp when the prescription expires")
    private Instant expiresAt;
}

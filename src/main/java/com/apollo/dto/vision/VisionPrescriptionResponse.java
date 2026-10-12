package com.apollo.dto.vision;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Unset optional values are omitted rather than serialized as null. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Glasses (vision) prescription issued by a doctor")
public class VisionPrescriptionResponse {

    @Schema(description = "Vision prescription unique identifier", example = "c2eebc99-9c0b-4ef8-bb6d-6bb9bd380a33")
    private UUID id;

    @Schema(description = "Patient profile UUID", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID patientId;

    @Schema(description = "Issuing doctor profile UUID", example = "d3eebc99-9c0b-4ef8-bb6d-6bb9bd380a44")
    private UUID doctorId;

    @Schema(description = "Issuing doctor's full name", example = "Dr. Gregory House")
    private String doctorName;

    @Schema(description = "Optional linked clinical encounter UUID")
    private UUID encounterId;

    @Schema(description = "Right eye (OD). Always present, possibly empty.")
    private VisionEyeResponse rightEye;

    @Schema(description = "Left eye (OS). Always present, possibly empty.")
    private VisionEyeResponse leftEye;

    @Schema(description = "Pupillary distance in millimetres", example = "63.0")
    private BigDecimal pupillaryDistanceMm;

    @Schema(description = "Free-text notes", example = "Progressive lenses.")
    private String notes;

    @Schema(description = "Timestamp when the prescription was issued")
    private Instant issuedAt;

    @Schema(description = "Timestamp when the prescription stops being valid. Absent when there is no set validity.")
    private Instant expiresAt;
}

package com.apollo.dto.vision;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Field rules are enforced by the vision prescription service rather than bean-validation annotations,
 * because the API contract requires a readable {@code message} and the global handler reports bean-validation
 * failures with a generic message. {@code doctorId}, {@code doctorName} and {@code issuedAt} sent by a client
 * are ignored: they are always taken from the authenticated doctor and the server clock.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload for issuing a glasses (vision) prescription")
public class CreateVisionPrescriptionRequest {

    @Schema(description = "UUID of the patient recipient", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID patientId;

    @Schema(description = "Optional linked clinical encounter UUID. Must belong to the same patient.")
    private UUID encounterId;

    @Schema(description = "Right eye (OD)")
    private VisionEyeRequest rightEye;

    @Schema(description = "Left eye (OS)")
    private VisionEyeRequest leftEye;

    @Schema(description = "Pupillary distance in millimetres, 35 to 90, one decimal", example = "63", type = "number")
    private BigDecimal pupillaryDistanceMm;

    @Schema(description = "Free-text notes, up to 1000 characters", example = "Progressive lenses.")
    private String notes;

    @Schema(description = "Optional end of validity. A past date is accepted. Absent means no set validity.")
    private Instant expiresAt;
}

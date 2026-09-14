package com.apollo.dto.lab;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Structured numerical lab test result for charting trends and clinical monitoring")
public class LabTestResultResponse {

    @Schema(description = "Unique lab test result UUID", example = "d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a55")
    private UUID id;

    @Schema(description = "Patient profile UUID", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID patientId;

    @Schema(description = "Ordering clinician or technician UUID", example = "e2eebc99-9c0b-4ef8-bb6d-6bb9bd380a66")
    private UUID doctorId;

    @Schema(description = "Name of the clinician or technician who recorded the test", example = "Dr. Gregory House")
    private String doctorName;

    @Schema(description = "Name of the diagnostic test or biomarker", example = "Fasting Blood Glucose")
    private String testName;

    @Schema(description = "Numerical result of the test", example = "95.5")
    private BigDecimal numericValue;

    @Schema(description = "Standard unit of measurement", example = "mg/dL")
    private String unit;

    @Schema(description = "Timestamp when the specimen was recorded")
    private Instant recordedAt;

    @Schema(description = "Audit timestamp of record creation")
    private Instant createdAt;
}

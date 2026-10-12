package com.apollo.dto.lab;

import com.apollo.domain.enums.LabResultFlag;
import com.apollo.domain.enums.LabResultSource;
import com.apollo.domain.enums.LabResultStatus;
import com.apollo.domain.enums.LabValueQualifier;
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
@Schema(description = "Lab test result: a numeric, censored or textual value with optional reference range, flag and comment")
public class LabTestResultResponse {

    @Schema(description = "Unique lab test result UUID", example = "d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a55")
    private UUID id;

    @Schema(description = "Patient profile UUID", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID patientId;

    @Schema(description = "Ordering clinician or technician UUID. Null for patient-reported results.", example = "e2eebc99-9c0b-4ef8-bb6d-6bb9bd380a66", nullable = true)
    private UUID doctorId;

    @Schema(description = "Name of the clinician or technician who recorded the test. Null for patient-reported results.", example = "Dr. Gregory House", nullable = true)
    private String doctorName;

    @Schema(description = "Name of the diagnostic test or biomarker", example = "Fasting Blood Glucose")
    private String testName;

    @Schema(description = "Numerical result. Null for non-numeric results; holds the bound when qualifier is set.", example = "95.5", nullable = true)
    private BigDecimal numericValue;

    @Schema(description = "Non-numeric result", example = "Positive", nullable = true)
    private String valueText;

    @Schema(description = "Censored value marker (< or >)", example = "<", nullable = true)
    private LabValueQualifier qualifier;

    @Schema(description = "Unit of measurement. Empty string when the result has none.", example = "mg/dL")
    private String unit;

    @Schema(description = "Lower bound of the reference range as printed by the lab", nullable = true)
    private BigDecimal referenceLow;

    @Schema(description = "Upper bound of the reference range as printed by the lab", nullable = true)
    private BigDecimal referenceHigh;

    @Schema(description = "Interpretation flag the entering doctor saw", nullable = true)
    private LabResultFlag flag;

    @Schema(description = "Free-text lab comment", nullable = true)
    private String comment;

    @Schema(description = "DRAFT results are visible only to the doctor who wrote them")
    private LabResultStatus status;

    @Schema(description = "Who recorded the result: the patient or a verifying clinician")
    private LabResultSource source;

    @Schema(description = "Panel UUID shared by every record saved in the same panel submission", nullable = true)
    private UUID panelId;

    @Schema(description = "Template the result was entered from", example = "cbc", nullable = true)
    private String templateCode;

    @Schema(description = "Version of the template the result was entered from", nullable = true)
    private Integer templateVersion;

    @Schema(description = "Template field key of this entry within its panel", example = "hgb", nullable = true)
    private String key;

    @Schema(description = "Timestamp when the specimen was recorded")
    private Instant recordedAt;

    @Schema(description = "Audit timestamp of record creation")
    private Instant createdAt;
}

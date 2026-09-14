package com.apollo.dto.doctor;

import com.apollo.dto.vault.ClinicalEncounterSummaryDto;
import com.apollo.dto.vault.HealthConditionResponse;
import com.apollo.dto.vault.PatientProfileSummaryDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Unlocked patient vault details returned after successful PIN verification handshake")
public class UnlockedVaultResponse {

    @Schema(description = "Patient basic demographic profile")
    private PatientProfileSummaryDto patient;

    @Schema(description = "Patient declared and doctor-verified allergies")
    private List<HealthConditionResponse> allergies;

    @Schema(description = "Patient declared and doctor-verified chronic conditions")
    private List<HealthConditionResponse> chronicConditions;

    @Schema(description = "All recorded health conditions and past medical history")
    private List<HealthConditionResponse> allConditions;

    @Schema(description = "Chronological past consultation encounter history")
    private List<ClinicalEncounterSummaryDto> encounterHistory;

    @Schema(description = "Scoped 24-hour active vault session UUID", example = "f4eebc99-9c0b-4ef8-bb6d-6bb9bd380a77")
    private java.util.UUID activeSessionId;

    @Schema(description = "Timestamp when the 24-hour consultation session expires")
    private java.time.Instant sessionExpiresAt;

    @Schema(description = "Active and past prescriptions (accessible to clinicians and pharmacists)")
    private List<com.apollo.dto.prescription.PrescriptionResponse> prescriptions;

    @Schema(description = "Biometric and diagnostic lab test results")
    private List<com.apollo.dto.lab.LabTestResultResponse> testResults;
}

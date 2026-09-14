package com.apollo.controller;

import com.apollo.domain.enums.PrescriptionStatus;
import com.apollo.dto.lab.LabTestResultResponse;
import com.apollo.dto.prescription.PrescriptionResponse;
import com.apollo.dto.vault.AccessGrantResponse;
import com.apollo.dto.vault.CreateHealthConditionRequest;
import com.apollo.dto.vault.HealthConditionResponse;
import com.apollo.dto.vault.PatientVaultTimelineResponse;
import com.apollo.exception.ErrorResponse;
import com.apollo.security.CustomUserDetails;
import com.apollo.service.LabTestResultService;
import com.apollo.service.PatientVaultService;
import com.apollo.service.PrescriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patient/vault")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PATIENT')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Patient Vault", description = "Endpoints for managing baseline health data, temporary consultation access codes, chronological timeline, and lab results")
public class PatientVaultController {

    private final PatientVaultService patientVaultService;
    private final PrescriptionService prescriptionService;
    private final LabTestResultService labTestResultService;

    @Operation(summary = "Add a baseline health condition or allergy", description = "Records a foundational patient-declared medical condition in the vault.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Condition recorded successfully",
                    content = @Content(schema = @Schema(implementation = HealthConditionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ROLE_PATIENT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/conditions")
    public ResponseEntity<HealthConditionResponse> addCondition(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateHealthConditionRequest request) {
        HealthConditionResponse response = patientVaultService.addCondition(userDetails.getProfileId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "List all patient health conditions", description = "Retrieves all health conditions, allergies, and past history recorded for the authenticated patient.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of conditions retrieved",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = HealthConditionResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ROLE_PATIENT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/conditions")
    public ResponseEntity<List<HealthConditionResponse>> getConditions(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<HealthConditionResponse> response = patientVaultService.getConditions(userDetails.getProfileId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete a patient-declared health condition", description = "Removes a condition from the vault. Patients can only delete PATIENT_DECLARED records.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Condition successfully removed"),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Cannot delete doctor-verified conditions",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Condition not found or does not belong to patient",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/conditions/{conditionId}")
    public ResponseEntity<Void> deleteCondition(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID conditionId) {
        patientVaultService.deleteCondition(userDetails.getProfileId(), conditionId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Generate a consultation access grant PIN", description = "Generates a random 6-digit access code valid for 15 minutes to unlock doctor append access. Revokes any older open tokens.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Access grant generated successfully",
                    content = @Content(schema = @Schema(implementation = AccessGrantResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ROLE_PATIENT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/access-grants")
    public ResponseEntity<AccessGrantResponse> generateAccessGrant(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        AccessGrantResponse response = patientVaultService.generateAccessGrant(userDetails.getProfileId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get consolidated chronological patient vault timeline", description = "Retrieves complete chronological view of demographics, baseline conditions, and appended clinical consultations.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vault timeline retrieved",
                    content = @Content(schema = @Schema(implementation = PatientVaultTimelineResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ROLE_PATIENT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/timeline")
    public ResponseEntity<PatientVaultTimelineResponse> getTimeline(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        PatientVaultTimelineResponse response = patientVaultService.getTimeline(userDetails.getProfileId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "List patient prescriptions", description = "Retrieves all prescriptions issued for the authenticated patient, optionally filtered by status (ACTIVE, FULFILLED, CANCELLED).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of prescriptions retrieved successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = PrescriptionResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ROLE_PATIENT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/prescriptions")
    public ResponseEntity<List<PrescriptionResponse>> getPrescriptions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) PrescriptionStatus status) {
        List<PrescriptionResponse> response = prescriptionService.getPatientPrescriptions(userDetails.getProfileId(), status);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get patient diagnostic lab test results",
            description = "Retrieves time-series lab test results for the patient sorted chronologically ascending (recordedAt ASC) for trend charting. Optionally filtered by testName.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lab test results retrieved successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = LabTestResultResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ROLE_PATIENT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/test-results")
    public ResponseEntity<List<LabTestResultResponse>> getTestResults(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String testName) {
        List<LabTestResultResponse> response = labTestResultService.getPatientTestResults(userDetails.getProfileId(), testName);
        return ResponseEntity.ok(response);
    }
}

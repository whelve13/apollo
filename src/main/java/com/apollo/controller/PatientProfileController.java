package com.apollo.controller;

import com.apollo.dto.patient.PatientProfileResponse;
import com.apollo.dto.patient.UpdatePatientProfileRequest;
import com.apollo.exception.ErrorResponse;
import com.apollo.security.CustomUserDetails;
import com.apollo.service.PatientVaultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/patient")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PATIENT')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Patient Profile", description = "Endpoints for managing patient profile demographics and baseline attributes")
public class PatientProfileController {

    private final PatientVaultService patientVaultService;

    @Operation(summary = "Get authenticated patient profile", description = "Retrieves demographic and baseline attributes for the authenticated patient.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Patient profile retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PatientProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ROLE_PATIENT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Patient profile not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/profile")
    public ResponseEntity<PatientProfileResponse> getProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        PatientProfileResponse response = patientVaultService.getProfile(userDetails.getProfileId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update patient baseline attributes and profile", description = "Updates baseline attributes (gender, height, weight) for the authenticated patient.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Patient profile updated successfully",
                    content = @Content(schema = @Schema(implementation = PatientProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ROLE_PATIENT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Patient profile not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/profile")
    public ResponseEntity<PatientProfileResponse> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdatePatientProfileRequest request) {
        PatientProfileResponse response = patientVaultService.updateProfile(userDetails.getProfileId(), request);
        return ResponseEntity.ok(response);
    }
}

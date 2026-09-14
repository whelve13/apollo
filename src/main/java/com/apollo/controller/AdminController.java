package com.apollo.controller;

import com.apollo.dto.admin.DoctorRoleUpdateResponse;
import com.apollo.dto.admin.UpdateDoctorRoleRequest;
import com.apollo.exception.ErrorResponse;
import com.apollo.service.AdminService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Operations", description = "Administrative and demo management endpoints for live presentations")
public class AdminController {

    private final AdminService adminService;

    @Operation(summary = "Switch doctor permission role for demo presentations",
            description = "Dynamically updates a doctor's active permission role (e.g. to LAB_TECHNICIAN, PHARMACIST, or GENERAL_PRACTITIONER) so role permission boundaries can be presented during live demonstrations.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Doctor role updated successfully",
                    content = @Content(schema = @Schema(implementation = DoctorRoleUpdateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN or DOCTOR role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Doctor profile not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/doctors/{doctorId}/role")
    public ResponseEntity<DoctorRoleUpdateResponse> updateDoctorRole(
            @PathVariable UUID doctorId,
            @Valid @RequestBody UpdateDoctorRoleRequest request) {
        DoctorRoleUpdateResponse response = adminService.updateDoctorRole(doctorId, request);
        return ResponseEntity.ok(response);
    }
}

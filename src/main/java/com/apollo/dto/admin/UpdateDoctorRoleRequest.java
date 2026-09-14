package com.apollo.dto.admin;

import com.apollo.domain.enums.DoctorRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for updating a doctor's active permission role for presentation demos")
public class UpdateDoctorRoleRequest {

    @NotNull(message = "Doctor role is required")
    @Schema(description = "Target doctor permission role", example = "LAB_TECHNICIAN")
    private DoctorRole doctorRole;
}

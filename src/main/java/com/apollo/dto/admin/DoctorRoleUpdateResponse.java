package com.apollo.dto.admin;

import com.apollo.domain.enums.DoctorRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response confirming doctor permission role update")
public class DoctorRoleUpdateResponse {

    @Schema(description = "Doctor profile UUID", example = "d3eebc99-9c0b-4ef8-bb6d-6bb9bd380a44")
    private UUID doctorId;

    @Schema(description = "Doctor's full name", example = "Dr. Gregory House")
    private String doctorName;

    @Schema(description = "Updated doctor permission role", example = "LAB_TECHNICIAN")
    private DoctorRole doctorRole;

    @Schema(description = "Status confirmation message", example = "Doctor permission role successfully updated.")
    private String message;
}

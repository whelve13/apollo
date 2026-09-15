package com.apollo.dto.vault;

import com.apollo.domain.enums.HealthConditionType;
import com.apollo.domain.enums.SourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Health condition or allergy record details")
public class HealthConditionResponse {

    @Schema(description = "Condition unique identifier", example = "c2eebc99-9c0b-4ef8-bb6d-6bb9bd380a22")
    private UUID id;

    @Schema(description = "Condition title or diagnosis name", example = "Penicillin Allergy")
    private String title;

    @Schema(description = "Category of condition", example = "ALLERGY")
    private HealthConditionType type;

    @Schema(description = "Source origin of the record", example = "PATIENT_DECLARED")
    private SourceType sourceType;

    @Schema(description = "Date recorded or reported", example = "2023-01-15")
    private LocalDate dateRecorded;

    @Schema(description = "Optional notes or details regarding condition", example = "Mild hives on exposure")
    private String notes;

    @Schema(description = "Timestamp when saved in vault")
    private Instant createdAt;
}

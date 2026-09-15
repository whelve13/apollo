package com.apollo.dto.vault;

import com.apollo.domain.enums.HealthConditionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
@Schema(description = "Individual baseline health condition or allergy entry for synchronization")
public record BaselineConditionItem(
    @NotBlank(message = "Title is required")
    @Schema(example = "Penicillin Allergy")
    String title,

    @NotNull(message = "Condition type is required")
    @Schema(example = "ALLERGY")
    HealthConditionType type,

    @Schema(description = "Optional clinical or personal notes regarding the condition", example = "Diagnosed in childhood")
    String notes
) {}

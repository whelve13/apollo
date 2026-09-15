package com.apollo.dto.vault;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Builder;

import java.util.List;

@Builder
@Schema(description = "Batch payload to replace and synchronize patient-declared baseline conditions")
public record SyncBaselineConditionsRequest(
    @Valid
    @Schema(description = "List of baseline condition items to set for the patient")
    List<BaselineConditionItem> conditions
) {}

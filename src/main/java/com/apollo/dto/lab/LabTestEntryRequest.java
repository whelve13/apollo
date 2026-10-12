package com.apollo.dto.lab;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "One value of a lab test panel")
public class LabTestEntryRequest extends LabResultValueRequest {

    @Size(max = 100, message = "Entry key must not exceed 100 characters")
    @Schema(description = "Stable template field key, returned unchanged so the client can rebuild the panel", example = "hgb")
    private String key;
}

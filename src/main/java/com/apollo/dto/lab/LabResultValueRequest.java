package com.apollo.dto.lab;

import com.apollo.domain.enums.LabResultFlag;
import com.apollo.domain.enums.LabValueQualifier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * The value-bearing fields shared by a standalone lab result and a single panel entry.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ValidLabResultValue
public abstract class LabResultValueRequest {

    @NotBlank(message = "Test name is required")
    @Size(max = 150, message = "Test name must not exceed 150 characters")
    @Schema(description = "Name of the diagnostic test or biomarker. Free text; trimmed and compared case-insensitively.", example = "Fasting Blood Glucose")
    private String testName;

    @Schema(description = "Numerical result. Optional: omit for non-numeric results. With a qualifier it holds the detection bound.", example = "95.5")
    private BigDecimal numericValue;

    @Size(max = 500, message = "Text value must not exceed 500 characters")
    @Schema(description = "Non-numeric result such as Positive, ++ or 1:80", example = "Positive")
    private String valueText;

    @Schema(description = "Marks a censored value (< or >). Only allowed together with numericValue, which then holds the bound.",
            example = "<")
    private LabValueQualifier qualifier;

    @Size(max = 50, message = "Unit must not exceed 50 characters")
    @Schema(description = "Unit of measurement. May be empty or omitted (pH, INR, text results).", example = "mg/dL")
    private String unit;

    @Schema(description = "Lower bound of the reference range as printed by the lab", example = "70")
    private BigDecimal referenceLow;

    @Schema(description = "Upper bound of the reference range as printed by the lab", example = "99")
    private BigDecimal referenceHigh;

    @Schema(description = "Interpretation flag the entering doctor saw")
    private LabResultFlag flag;

    @Size(max = 2000, message = "Comment must not exceed 2000 characters")
    @Schema(description = "Free-text lab comment", example = "Sample slightly hemolyzed")
    private String comment;
}

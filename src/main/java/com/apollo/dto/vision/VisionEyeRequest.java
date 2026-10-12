package com.apollo.dto.vision;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Numbers are read as {@link BigDecimal} so quarter-diopter steps are checked exactly and a fractional
 * axis (for example 90.5) is rejected instead of being truncated by the JSON mapper.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Correction for one eye. Omit the object, or send {}, when the eye is not prescribed.")
public class VisionEyeRequest {

    @Schema(description = "Sphere in diopters, -20 to +20 in steps of 0.25. 0 means plano. Required when the eye has any value.",
            example = "-2.5", type = "number")
    private BigDecimal sphere;

    @Schema(description = "Cylinder (astigmatism) in diopters, -8 to +8 in steps of 0.25. 0 or absent means none.",
            example = "-0.75", type = "number")
    private BigDecimal cylinder;

    @Schema(description = "Axis in degrees, a whole number from 0 to 180. Required with a non-zero cylinder.",
            example = "180", type = "integer")
    private BigDecimal axis;

    @Schema(description = "Near addition in diopters, +0.25 to +4 in steps of 0.25.",
            example = "1.5", type = "number")
    private BigDecimal add;
}

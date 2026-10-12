package com.apollo.dto.vision;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Correction for one eye. An eye that is not prescribed is an empty object.")
public class VisionEyeResponse {

    @Schema(description = "Sphere in diopters. 0 means plano.", example = "-2.5")
    private BigDecimal sphere;

    @Schema(description = "Cylinder (astigmatism) in diopters. Absent when none.", example = "-0.75")
    private BigDecimal cylinder;

    @Schema(description = "Axis in degrees, 0 to 180. Present only with a non-zero cylinder.", example = "180")
    private Integer axis;

    @Schema(description = "Near addition in diopters, positive.", example = "1.5")
    private BigDecimal add;
}

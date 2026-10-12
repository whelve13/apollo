package com.apollo.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Correction values for one eye. Powers are exact decimals (quarter-diopter steps), never floating point,
 * so {@code -2.25} round-trips unchanged. Column names are supplied per eye by {@link VisionPrescription}.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisionEyeValues {

    @Column(name = "sphere", precision = 4, scale = 2)
    private BigDecimal sphere;

    @Column(name = "cylinder", precision = 4, scale = 2)
    private BigDecimal cylinder;

    @Column(name = "axis")
    private Short axis;

    @Column(name = "add_power", precision = 4, scale = 2)
    private BigDecimal addPower;
}

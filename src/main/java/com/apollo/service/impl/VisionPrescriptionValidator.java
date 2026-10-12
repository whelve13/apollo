package com.apollo.service.impl;

import com.apollo.domain.entity.VisionEyeValues;
import com.apollo.dto.vision.VisionEyeRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Server-side counterpart of the frontend form rules for glasses prescriptions. Violations throw
 * {@link IllegalArgumentException}, which the global handler turns into a 400 carrying the readable message.
 * <p>
 * Range checks always run before any scale arithmetic, so an exotic exponent such as {@code 1E+999999999}
 * is rejected cheaply instead of being expanded.
 */
final class VisionPrescriptionValidator {

    static final int NOTES_MAX_LENGTH = 1000;

    private static final BigDecimal STEP = new BigDecimal("0.25");
    private static final BigDecimal SPHERE_LIMIT = BigDecimal.valueOf(20);
    private static final BigDecimal CYLINDER_LIMIT = BigDecimal.valueOf(8);
    private static final BigDecimal ADD_MAX = BigDecimal.valueOf(4);
    private static final BigDecimal AXIS_MAX = BigDecimal.valueOf(180);
    private static final BigDecimal PD_MIN = BigDecimal.valueOf(35);
    private static final BigDecimal PD_MAX = BigDecimal.valueOf(90);

    private VisionPrescriptionValidator() {
    }

    /**
     * Validates one eye and returns its normalised values, or {@code null} when the eye carries no value
     * (absent, or an empty object), meaning "not prescribed".
     * <p>
     * A cylinder of {@code 0} means no astigmatism correction: it is dropped together with any axis sent
     * with it. An axis with no cylinder at all is rejected rather than silently ignored.
     */
    static VisionEyeValues normaliseEye(VisionEyeRequest eye) {
        if (eye == null) {
            return null;
        }
        BigDecimal sphere = eye.getSphere();
        BigDecimal cylinder = eye.getCylinder();
        BigDecimal axis = eye.getAxis();
        BigDecimal add = eye.getAdd();

        if (sphere == null && cylinder == null && axis == null && add == null) {
            return null;
        }

        if (sphere == null) {
            throw invalid("Enter the sphere, or 0 for plano.");
        }
        BigDecimal normalisedSphere = quarterDiopters(sphere, SPHERE_LIMIT.negate(), SPHERE_LIMIT,
                "The sphere must be between -20.00 and +20.00.");

        Short normalisedAxis = axis != null ? wholeDegrees(axis) : null;

        BigDecimal normalisedCylinder = null;
        if (cylinder != null) {
            BigDecimal checked = quarterDiopters(cylinder, CYLINDER_LIMIT.negate(), CYLINDER_LIMIT,
                    "The cylinder must be between -8.00 and +8.00.");
            if (checked.signum() != 0) {
                if (normalisedAxis == null) {
                    throw invalid("Enter the axis for this cylinder.");
                }
                normalisedCylinder = checked;
            } else {
                normalisedAxis = null;
            }
        } else if (normalisedAxis != null) {
            throw invalid("The axis only applies together with a cylinder.");
        }

        BigDecimal normalisedAdd = null;
        if (add != null) {
            normalisedAdd = quarterDiopters(add, STEP, ADD_MAX, "The add must be between +0.25 and +4.00.");
        }

        return VisionEyeValues.builder()
                .sphere(normalisedSphere)
                .cylinder(normalisedCylinder)
                .axis(normalisedAxis)
                .addPower(normalisedAdd)
                .build();
    }

    /** Returns the distance rounded to one decimal, or {@code null} when none was sent. */
    static BigDecimal normalisePupillaryDistance(BigDecimal distance) {
        if (distance == null) {
            return null;
        }
        if (distance.compareTo(PD_MIN) < 0 || distance.compareTo(PD_MAX) > 0) {
            throw invalid("The pupillary distance should be between 35 and 90 mm.");
        }
        return distance.setScale(1, RoundingMode.HALF_UP);
    }

    /** Returns the trimmed notes, or {@code null} when blank. */
    static String normaliseNotes(String notes) {
        if (notes == null) {
            return null;
        }
        String trimmed = notes.trim();
        if (trimmed.length() > NOTES_MAX_LENGTH) {
            throw invalid("Notes can be at most " + NOTES_MAX_LENGTH + " characters.");
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static BigDecimal quarterDiopters(BigDecimal value, BigDecimal min, BigDecimal max, String rangeMessage) {
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw invalid(rangeMessage);
        }
        if (value.remainder(STEP).signum() != 0) {
            throw invalid("Use steps of 0.25.");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static Short wholeDegrees(BigDecimal axis) {
        boolean inRange = axis.signum() >= 0 && axis.compareTo(AXIS_MAX) <= 0;
        if (!inRange || axis.stripTrailingZeros().scale() > 0) {
            throw invalid("The axis must be a whole number from 0 to 180.");
        }
        return (short) axis.intValueExact();
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}

package com.apollo.domain.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A doctor-issued glasses prescription. Append-only: there is no status, edit or delete in this version.
 * An eye that is not prescribed has all of its columns null.
 */
@Entity
@Table(name = "vision_prescriptions", indexes = {
        @Index(name = "idx_vision_prescriptions_patient_issued", columnList = "patient_id, issued_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisionPrescription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private PatientProfile patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private DoctorProfile doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id", nullable = true)
    private ClinicalEncounter encounter;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "sphere", column = @Column(name = "right_sphere", precision = 4, scale = 2)),
            @AttributeOverride(name = "cylinder", column = @Column(name = "right_cylinder", precision = 4, scale = 2)),
            @AttributeOverride(name = "axis", column = @Column(name = "right_axis")),
            @AttributeOverride(name = "addPower", column = @Column(name = "right_add", precision = 4, scale = 2))
    })
    private VisionEyeValues rightEye;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "sphere", column = @Column(name = "left_sphere", precision = 4, scale = 2)),
            @AttributeOverride(name = "cylinder", column = @Column(name = "left_cylinder", precision = 4, scale = 2)),
            @AttributeOverride(name = "axis", column = @Column(name = "left_axis")),
            @AttributeOverride(name = "addPower", column = @Column(name = "left_add", precision = 4, scale = 2))
    })
    private VisionEyeValues leftEye;

    @Column(name = "pupillary_distance_mm", precision = 3, scale = 1)
    private BigDecimal pupillaryDistanceMm;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VisionPrescription that = (VisionPrescription) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

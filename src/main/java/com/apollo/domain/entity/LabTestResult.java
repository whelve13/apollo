package com.apollo.domain.entity;

import com.apollo.domain.enums.LabResultFlag;
import com.apollo.domain.enums.LabResultSource;
import com.apollo.domain.enums.LabResultStatus;
import com.apollo.domain.enums.LabValueQualifier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "lab_test_results", indexes = {
        @Index(name = "idx_lab_test_results_panel_id", columnList = "panel_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabTestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private PatientProfile patient;

    /** Null for patient-reported results. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    private DoctorProfile doctor;

    @Column(name = "test_name", nullable = false, length = 150)
    private String testName;

    /** Null for non-numeric results; holds the detection bound when {@link #qualifier} is set. */
    @Column(name = "numeric_value", precision = 12, scale = 4)
    private BigDecimal numericValue;

    @Column(name = "value_text", length = 500)
    private String valueText;

    @Enumerated(EnumType.STRING)
    @Column(name = "qualifier", length = 20)
    private LabValueQualifier qualifier;

    @Column(name = "unit", nullable = false, length = 50)
    private String unit;

    @Column(name = "reference_low", precision = 12, scale = 4)
    private BigDecimal referenceLow;

    @Column(name = "reference_high", precision = 12, scale = 4)
    private BigDecimal referenceHigh;

    @Enumerated(EnumType.STRING)
    @Column(name = "flag", length = 20)
    private LabResultFlag flag;

    @Column(name = "lab_comment", length = 2000)
    private String comment;

    @Builder.Default
    @ColumnDefault("'FINAL'")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LabResultStatus status = LabResultStatus.FINAL;

    @Builder.Default
    @ColumnDefault("'DOCTOR_VERIFIED'")
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private LabResultSource source = LabResultSource.DOCTOR_VERIFIED;

    /** Groups the records saved together by one panel submission; null for standalone records. */
    @Column(name = "panel_id")
    private UUID panelId;

    @Column(name = "template_code", length = 100)
    private String templateCode;

    @Column(name = "template_version")
    private Integer templateVersion;

    @Column(name = "entry_key", length = 100)
    private String entryKey;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LabTestResult that = (LabTestResult) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

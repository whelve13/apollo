package com.apollo.repository;

import com.apollo.domain.entity.LabTestResult;
import com.apollo.domain.enums.LabResultStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LabTestResultRepository extends JpaRepository<LabTestResult, UUID> {

    List<LabTestResult> findByPatientIdAndStatusOrderByRecordedAtAscCreatedAtAsc(UUID patientId, LabResultStatus status);

    List<LabTestResult> findByPatientIdAndStatusAndTestNameIgnoreCaseOrderByRecordedAtAscCreatedAtAsc(
            UUID patientId, LabResultStatus status, String testName);

    /**
     * Everything a clinician may see for a patient: all FINAL results plus that clinician's own drafts.
     */
    @Query("""
            select r from LabTestResult r
            where r.patient.id = :patientId
              and (r.status = com.apollo.domain.enums.LabResultStatus.FINAL or r.doctor.id = :doctorId)
            order by r.recordedAt asc, r.createdAt asc
            """)
    List<LabTestResult> findVisibleToDoctor(@Param("patientId") UUID patientId, @Param("doctorId") UUID doctorId);
}

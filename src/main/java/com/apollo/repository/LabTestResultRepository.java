package com.apollo.repository;

import com.apollo.domain.entity.LabTestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LabTestResultRepository extends JpaRepository<LabTestResult, UUID> {

    List<LabTestResult> findByPatientIdOrderByRecordedAtAscCreatedAtAsc(UUID patientId);

    List<LabTestResult> findByPatientIdAndTestNameIgnoreCaseOrderByRecordedAtAscCreatedAtAsc(UUID patientId, String testName);
}

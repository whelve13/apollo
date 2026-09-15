package com.apollo.repository;

import com.apollo.domain.entity.HealthCondition;
import com.apollo.domain.entity.PatientProfile;
import com.apollo.domain.enums.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface HealthConditionRepository extends JpaRepository<HealthCondition, UUID> {
    List<HealthCondition> findByPatientOrderByDateRecordedDescCreatedAtDesc(PatientProfile patient);
    List<HealthCondition> findByPatientIdOrderByDateRecordedDescCreatedAtDesc(UUID patientId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        DELETE FROM HealthCondition h 
        WHERE h.patient.id = :profileId 
          AND h.sourceType = com.apollo.domain.enums.SourceType.PATIENT_DECLARED
    """)
    void deleteByPatientProfileIdAndSourceTypePatientDeclared(@Param("profileId") UUID profileId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    void deleteByPatientIdAndSourceType(UUID patientId, SourceType sourceType);
}

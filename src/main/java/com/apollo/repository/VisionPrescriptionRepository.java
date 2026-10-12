package com.apollo.repository;

import com.apollo.domain.entity.VisionPrescription;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VisionPrescriptionRepository extends JpaRepository<VisionPrescription, UUID> {

    @EntityGraph(attributePaths = "doctor")
    List<VisionPrescription> findByPatientIdOrderByIssuedAtDescCreatedAtDesc(UUID patientId);
}

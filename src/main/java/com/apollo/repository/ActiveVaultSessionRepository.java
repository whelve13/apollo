package com.apollo.repository;

import com.apollo.domain.entity.ActiveVaultSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ActiveVaultSessionRepository extends JpaRepository<ActiveVaultSession, UUID> {

    boolean existsByDoctorIdAndPatientIdAndExpiresAtAfter(UUID doctorId, UUID patientId, Instant now);

    Optional<ActiveVaultSession> findTopByDoctorIdAndPatientIdAndExpiresAtAfterOrderByExpiresAtDesc(
            UUID doctorId, UUID patientId, Instant now);

    List<ActiveVaultSession> findByDoctorIdAndExpiresAtAfter(UUID doctorId, Instant now);

    List<ActiveVaultSession> findByPatientIdAndExpiresAtAfter(UUID patientId, Instant now);
}

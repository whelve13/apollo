package com.apollo.repository;

import com.apollo.domain.entity.AccessGrant;
import com.apollo.domain.entity.PatientProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccessGrantRepository extends JpaRepository<AccessGrant, UUID> {
    @Query("select g.patient.id from AccessGrant g where g.accessCode = :accessCode " +
            "and g.isUsed = false and g.revokedAt is null and g.expiresAt > :now")
    java.util.List<UUID> findPatientIdsForUnlock(String accessCode, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from AccessGrant g where g.accessCode = :accessCode " +
            "and g.isUsed = false and g.revokedAt is null and g.expiresAt > :now")
    java.util.List<AccessGrant> findUnlockCandidates(String accessCode, Instant now);

    @Modifying
    @Query("update AccessGrant g set g.revokedAt = :now, g.isUsed = true " +
            "where g.patient.id = :patientId and g.revokedAt is null")
    void revokeByPatientId(UUID patientId, Instant now);
    Optional<AccessGrant> findByAccessCodeAndIsUsedFalseAndExpiresAtAfter(String accessCode, Instant now);
    Optional<AccessGrant> findTopByPatientOrderByCreatedAtDesc(PatientProfile patient);
    Optional<AccessGrant> findTopByPatientIdOrderByCreatedAtDesc(UUID patientId);
    java.util.List<AccessGrant> findByPatientIdAndIsUsedFalse(UUID patientId);
}

package com.apollo.repository;

import com.apollo.domain.entity.ActiveVaultSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ActiveVaultSessionRepository extends JpaRepository<ActiveVaultSession, UUID> {

    void deleteByDoctorId(UUID doctorId);

    void deleteByDoctorIdAndPatientId(UUID doctorId, UUID patientId);

    @Query("select (count(s) > 0) from ActiveVaultSession s join s.accessGrant g " +
            "where s.doctor.id = :doctorId and s.patient.id = :patientId " +
            "and s.expiresAt > :now and g.expiresAt > :now and g.revokedAt is null")
    boolean existsByDoctorIdAndPatientIdAndExpiresAtAfter(UUID doctorId, UUID patientId, Instant now);

    Optional<ActiveVaultSession> findTopByDoctorIdAndPatientIdAndExpiresAtAfterOrderByExpiresAtDesc(
            UUID doctorId, UUID patientId, Instant now);

    @Query("select s from ActiveVaultSession s join fetch s.accessGrant g join fetch s.patient " +
            "where s.doctor.id = :doctorId and s.expiresAt > :now " +
            "and g.expiresAt > :now and g.revokedAt is null order by s.createdAt desc")
    List<ActiveVaultSession> findByDoctorIdAndExpiresAtAfter(UUID doctorId, Instant now);

    List<ActiveVaultSession> findByPatientIdAndExpiresAtAfter(UUID patientId, Instant now);
}

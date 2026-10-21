package com.apollo.repository;

import com.apollo.domain.entity.PatientProfile;
import com.apollo.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientProfileRepository extends JpaRepository<PatientProfile, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PatientProfile p where p.id = :patientId")
    Optional<PatientProfile> findByIdForAccessGrantUpdate(UUID patientId);
    Optional<PatientProfile> findByUser(User user);
    Optional<PatientProfile> findByUserId(UUID userId);
}

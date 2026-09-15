package com.apollo.service.impl;

import com.apollo.domain.entity.AccessGrant;
import com.apollo.domain.entity.ClinicalEncounter;
import com.apollo.domain.entity.HealthCondition;
import com.apollo.domain.entity.PatientProfile;
import com.apollo.domain.enums.SourceType;
import com.apollo.dto.patient.PatientProfileResponse;
import com.apollo.dto.patient.UpdatePatientProfileRequest;
import com.apollo.dto.vault.AccessGrantResponse;
import com.apollo.dto.vault.ClinicalEncounterSummaryDto;
import com.apollo.dto.vault.CreateHealthConditionRequest;
import com.apollo.dto.vault.HealthConditionResponse;
import com.apollo.dto.vault.PatientProfileSummaryDto;
import com.apollo.dto.vault.PatientVaultTimelineResponse;
import com.apollo.dto.vault.SyncBaselineConditionsRequest;
import com.apollo.exception.ResourceNotFoundException;
import com.apollo.repository.AccessGrantRepository;
import com.apollo.repository.ClinicalEncounterRepository;
import com.apollo.repository.HealthConditionRepository;
import com.apollo.repository.PatientProfileRepository;
import com.apollo.service.PatientVaultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatientVaultServiceImpl implements PatientVaultService {

    private final PatientProfileRepository patientProfileRepository;
    private final HealthConditionRepository healthConditionRepository;
    private final ClinicalEncounterRepository clinicalEncounterRepository;
    private final AccessGrantRepository accessGrantRepository;

    @Value("${apollo.vault.access-grant-validity-minutes:15}")
    private int validityMinutes;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public HealthConditionResponse addCondition(UUID patientProfileId, CreateHealthConditionRequest request) {
        PatientProfile patient = patientProfileRepository.findById(patientProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found with id: " + patientProfileId));

        HealthCondition condition = HealthCondition.builder()
                .patient(patient)
                .title(request.getTitle().trim())
                .type(request.getType())
                .sourceType(SourceType.PATIENT_DECLARED)
                .dateRecorded(request.getDateRecorded() != null ? request.getDateRecorded() : LocalDate.now())
                .build();

        condition = healthConditionRepository.save(condition);

        return mapToHealthConditionResponse(condition);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HealthConditionResponse> getConditions(UUID patientProfileId) {
        return healthConditionRepository.findByPatientIdOrderByDateRecordedDescCreatedAtDesc(patientProfileId)
                .stream()
                .map(this::mapToHealthConditionResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deleteCondition(UUID patientProfileId, UUID conditionId) {
        HealthCondition condition = healthConditionRepository.findById(conditionId)
                .orElseThrow(() -> new ResourceNotFoundException("Health condition not found with id: " + conditionId));

        if (!condition.getPatient().getId().equals(patientProfileId)) {
            throw new ResourceNotFoundException("Health condition not found with id: " + conditionId);
        }

        if (condition.getSourceType() != SourceType.PATIENT_DECLARED) {
            throw new AccessDeniedException("Patients cannot delete doctor-verified clinical conditions.");
        }

        healthConditionRepository.delete(condition);
    }

    @Override
    @Transactional
    public AccessGrantResponse generateAccessGrant(UUID patientProfileId) {
        PatientProfile patient = patientProfileRepository.findById(patientProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found with id: " + patientProfileId));

        // Invalidate any existing open (unused) grants for this patient
        List<AccessGrant> activeGrants = accessGrantRepository.findByPatientIdAndIsUsedFalse(patientProfileId);
        if (!activeGrants.isEmpty()) {
            activeGrants.forEach(grant -> grant.setUsed(true));
            accessGrantRepository.saveAll(activeGrants);
        }

        // Generate cryptographically secure 6-digit numeric token
        int codeNumber = secureRandom.nextInt(1_000_000);
        String accessCode = String.format("%06d", codeNumber);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(validityMinutes, ChronoUnit.MINUTES);

        AccessGrant newGrant = AccessGrant.builder()
                .patient(patient)
                .accessCode(accessCode)
                .expiresAt(expiresAt)
                .isUsed(false)
                .build();

        newGrant = accessGrantRepository.save(newGrant);

        long remainingSeconds = Math.max(0, Duration.between(now, expiresAt).getSeconds());

        return AccessGrantResponse.builder()
                .id(newGrant.getId())
                .accessCode(newGrant.getAccessCode())
                .expiresAt(newGrant.getExpiresAt())
                .remainingSeconds(remainingSeconds)
                .isUsed(newGrant.isUsed())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PatientVaultTimelineResponse getTimeline(UUID patientProfileId) {
        PatientProfile patient = patientProfileRepository.findById(patientProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found with id: " + patientProfileId));

        PatientProfileSummaryDto patientSummary = PatientProfileSummaryDto.builder()
                .id(patient.getId())
                .firstName(patient.getFirstName())
                .lastName(patient.getLastName())
                .dateOfBirth(patient.getDateOfBirth())
                .bloodType(patient.getBloodType())
                .gender(patient.getGender())
                .heightCm(patient.getHeightCm())
                .weightKg(patient.getWeightKg())
                .build();

        List<HealthConditionResponse> conditions = healthConditionRepository
                .findByPatientIdOrderByDateRecordedDescCreatedAtDesc(patientProfileId)
                .stream()
                .map(this::mapToHealthConditionResponse)
                .toList();

        List<ClinicalEncounterSummaryDto> encounters = clinicalEncounterRepository
                .findByPatientIdOrderByEncounterDateDescCreatedAtDesc(patientProfileId)
                .stream()
                .map(this::mapToClinicalEncounterSummary)
                .toList();

        return PatientVaultTimelineResponse.builder()
                .patient(patientSummary)
                .conditions(conditions)
                .encounters(encounters)
                .build();
    }

    @Override
    @Transactional
    public PatientProfileResponse updateProfile(UUID patientProfileId, UpdatePatientProfileRequest request) {
        PatientProfile patient = patientProfileRepository.findById(patientProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found with id: " + patientProfileId));

        if (request.gender() != null) {
            patient.setGender(request.gender().trim());
        }
        if (request.heightCm() != null) {
            patient.setHeightCm(request.heightCm());
        }
        if (request.weightKg() != null) {
            patient.setWeightKg(request.weightKg());
        }

        patient = patientProfileRepository.save(patient);

        return mapToPatientProfileResponse(patient);
    }

    @Override
    @Transactional(readOnly = true)
    public PatientProfileResponse getProfile(UUID patientProfileId) {
        PatientProfile patient = patientProfileRepository.findById(patientProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found with id: " + patientProfileId));
        return mapToPatientProfileResponse(patient);
    }

    @Override
    @Transactional
    public List<HealthConditionResponse> syncBaselineConditions(UUID patientProfileId, SyncBaselineConditionsRequest request) {
        PatientProfile patient = patientProfileRepository.findById(patientProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found with id: " + patientProfileId));

        // 1. Delete prior PATIENT_DECLARED conditions for this patient
        healthConditionRepository.deleteByPatientProfileIdAndSourceTypePatientDeclared(patientProfileId);

        // 2. Insert all items provided in SyncBaselineConditionsRequest with sourceType = PATIENT_DECLARED
        if (request != null && request.conditions() != null && !request.conditions().isEmpty()) {
            List<HealthCondition> newConditions = request.conditions().stream()
                    .filter(item -> item.title() != null && !item.title().trim().isEmpty())
                    .map(item -> HealthCondition.builder()
                            .patient(patient)
                            .title(item.title().trim())
                            .type(item.type())
                            .notes(item.notes())
                            .sourceType(SourceType.PATIENT_DECLARED)
                            .dateRecorded(LocalDate.now())
                            .build())
                    .toList();
            healthConditionRepository.saveAll(newConditions);
        }

        // 3. Return the updated list of health conditions
        return getConditions(patientProfileId);
    }

    private PatientProfileResponse mapToPatientProfileResponse(PatientProfile patient) {
        return PatientProfileResponse.builder()
                .id(patient.getId())
                .userId(patient.getUser() != null ? patient.getUser().getId() : null)
                .firstName(patient.getFirstName())
                .lastName(patient.getLastName())
                .dateOfBirth(patient.getDateOfBirth())
                .bloodType(patient.getBloodType())
                .gender(patient.getGender())
                .heightCm(patient.getHeightCm())
                .weightKg(patient.getWeightKg())
                .createdAt(patient.getCreatedAt())
                .build();
    }

    private HealthConditionResponse mapToHealthConditionResponse(HealthCondition condition) {
        return HealthConditionResponse.builder()
                .id(condition.getId())
                .title(condition.getTitle())
                .type(condition.getType())
                .sourceType(condition.getSourceType())
                .notes(condition.getNotes())
                .dateRecorded(condition.getDateRecorded())
                .createdAt(condition.getCreatedAt())
                .build();
    }

    private ClinicalEncounterSummaryDto mapToClinicalEncounterSummary(ClinicalEncounter encounter) {
        String doctorName = "Dr. " + encounter.getDoctor().getFirstName() + " " + encounter.getDoctor().getLastName();
        return ClinicalEncounterSummaryDto.builder()
                .id(encounter.getId())
                .doctorId(encounter.getDoctor().getId())
                .doctorName(doctorName)
                .doctorSpecialty(encounter.getDoctor().getSpecialty())
                .encounterDate(encounter.getEncounterDate())
                .diagnosis(encounter.getDiagnosis())
                .clinicalNotes(encounter.getClinicalNotes())
                .createdAt(encounter.getCreatedAt())
                .build();
    }
}

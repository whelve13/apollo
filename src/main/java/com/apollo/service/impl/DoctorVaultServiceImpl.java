package com.apollo.service.impl;

import com.apollo.domain.entity.AccessGrant;
import com.apollo.domain.entity.ActiveVaultSession;
import com.apollo.domain.entity.ClinicalEncounter;
import com.apollo.domain.entity.DoctorProfile;
import com.apollo.domain.entity.HealthCondition;
import com.apollo.domain.entity.LabTestResult;
import com.apollo.domain.entity.PatientProfile;
import com.apollo.domain.entity.Prescription;
import com.apollo.domain.enums.DoctorRole;
import com.apollo.domain.enums.HealthConditionType;
import com.apollo.domain.enums.SourceType;
import com.apollo.dto.doctor.ClinicalEncounterResponse;
import com.apollo.dto.doctor.CreateEncounterRequest;
import com.apollo.dto.doctor.DoctorConditionInput;
import com.apollo.dto.doctor.UnlockVaultRequest;
import com.apollo.dto.doctor.UnlockedVaultResponse;
import com.apollo.dto.lab.LabTestResultResponse;
import com.apollo.dto.prescription.PrescriptionResponse;
import com.apollo.dto.vault.ClinicalEncounterSummaryDto;
import com.apollo.dto.vault.HealthConditionResponse;
import com.apollo.dto.vault.PatientProfileSummaryDto;
import com.apollo.exception.InvalidAccessGrantException;
import com.apollo.exception.ResourceNotFoundException;
import com.apollo.repository.AccessGrantRepository;
import com.apollo.repository.ActiveVaultSessionRepository;
import com.apollo.repository.ClinicalEncounterRepository;
import com.apollo.repository.DoctorProfileRepository;
import com.apollo.repository.HealthConditionRepository;
import com.apollo.repository.LabTestResultRepository;
import com.apollo.repository.PatientProfileRepository;
import com.apollo.repository.PrescriptionRepository;
import com.apollo.service.DoctorVaultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorVaultServiceImpl implements DoctorVaultService {

    private final DoctorProfileRepository doctorProfileRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final AccessGrantRepository accessGrantRepository;
    private final HealthConditionRepository healthConditionRepository;
    private final ClinicalEncounterRepository clinicalEncounterRepository;
    private final ActiveVaultSessionRepository activeVaultSessionRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final LabTestResultRepository labTestResultRepository;

    @Override
    @Transactional
    public UnlockedVaultResponse unlockVault(UUID doctorProfileId, UnlockVaultRequest request) {
        DoctorProfile doctor = doctorProfileRepository.findById(doctorProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found: " + doctorProfileId));

        // Find active, unexpired, and unused grant
        AccessGrant grant = accessGrantRepository
                .findByAccessCodeAndIsUsedFalseAndExpiresAtAfter(request.getAccessCode().trim(), Instant.now())
                .orElseThrow(() -> new InvalidAccessGrantException("Invalid or expired consultation access PIN."));

        // Consume grant immediately upon handshake
        grant.setUsed(true);
        accessGrantRepository.save(grant);

        PatientProfile patient = grant.getPatient();

        // Establish or extend 24-hour consultation session
        Instant sessionExpiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
        ActiveVaultSession session = activeVaultSessionRepository
                .findTopByDoctorIdAndPatientIdAndExpiresAtAfterOrderByExpiresAtDesc(doctor.getId(), patient.getId(), Instant.now())
                .orElse(null);

        if (session != null) {
            session.setExpiresAt(sessionExpiresAt);
            session = activeVaultSessionRepository.save(session);
        } else {
            session = ActiveVaultSession.builder()
                    .doctor(doctor)
                    .patient(patient)
                    .expiresAt(sessionExpiresAt)
                    .build();
            session = activeVaultSessionRepository.save(session);
        }

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

        DoctorRole role = doctor.getDoctorRole();

        List<HealthConditionResponse> allConditions = Collections.emptyList();
        List<HealthConditionResponse> allergies = Collections.emptyList();
        List<HealthConditionResponse> chronicConditions = Collections.emptyList();
        List<ClinicalEncounterSummaryDto> encounterHistory = Collections.emptyList();
        List<PrescriptionResponse> prescriptions = Collections.emptyList();
        List<LabTestResultResponse> testResults = Collections.emptyList();

        // Granular view filtering according to doctor role
        if (role == DoctorRole.GENERAL_PRACTITIONER || role == DoctorRole.SPECIALIST) {
            List<HealthCondition> allConditionsList = healthConditionRepository
                    .findByPatientIdOrderByDateRecordedDescCreatedAtDesc(patient.getId());

            allConditions = allConditionsList.stream()
                    .map(this::mapToHealthConditionResponse)
                    .toList();

            allergies = allConditionsList.stream()
                    .filter(c -> c.getType() == HealthConditionType.ALLERGY)
                    .map(this::mapToHealthConditionResponse)
                    .toList();

            chronicConditions = allConditionsList.stream()
                    .filter(c -> c.getType() == HealthConditionType.CHRONIC_CONDITION)
                    .map(this::mapToHealthConditionResponse)
                    .toList();

            encounterHistory = clinicalEncounterRepository
                    .findByPatientIdOrderByEncounterDateDescCreatedAtDesc(patient.getId())
                    .stream()
                    .map(this::mapToClinicalEncounterSummary)
                    .toList();

            prescriptions = prescriptionRepository
                    .findByPatientIdOrderByIssuedAtDesc(patient.getId())
                    .stream()
                    .map(this::mapToPrescriptionResponse)
                    .toList();

            testResults = labTestResultRepository
                    .findByPatientIdOrderByRecordedAtAscCreatedAtAsc(patient.getId())
                    .stream()
                    .map(this::mapToLabTestResultResponse)
                    .toList();

        } else if (role == DoctorRole.LAB_TECHNICIAN) {
            // Lab tech only sees demographics and test results
            testResults = labTestResultRepository
                    .findByPatientIdOrderByRecordedAtAscCreatedAtAsc(patient.getId())
                    .stream()
                    .map(this::mapToLabTestResultResponse)
                    .toList();

        } else if (role == DoctorRole.PHARMACIST) {
            // Pharmacist only sees demographics and prescriptions
            prescriptions = prescriptionRepository
                    .findByPatientIdOrderByIssuedAtDesc(patient.getId())
                    .stream()
                    .map(this::mapToPrescriptionResponse)
                    .toList();
        }

        return UnlockedVaultResponse.builder()
                .patient(patientSummary)
                .allergies(allergies)
                .chronicConditions(chronicConditions)
                .allConditions(allConditions)
                .encounterHistory(encounterHistory)
                .activeSessionId(session.getId())
                .sessionExpiresAt(session.getExpiresAt())
                .prescriptions(prescriptions)
                .testResults(testResults)
                .build();
    }

    @Override
    @Transactional
    public ClinicalEncounterResponse createEncounter(UUID doctorProfileId, CreateEncounterRequest request) {
        DoctorProfile doctor = doctorProfileRepository.findById(doctorProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found: " + doctorProfileId));

        // Role restriction: Only GPs and Specialists can log encounters
        if (doctor.getDoctorRole() != DoctorRole.GENERAL_PRACTITIONER && doctor.getDoctorRole() != DoctorRole.SPECIALIST) {
            throw new AccessDeniedException("Only general practitioners and specialists are authorized to append clinical encounters.");
        }

        PatientProfile patient = patientProfileRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found: " + request.getPatientId()));

        // Active 24-hour consultation session validation
        boolean hasActiveSession = activeVaultSessionRepository
                .existsByDoctorIdAndPatientIdAndExpiresAtAfter(doctor.getId(), patient.getId(), Instant.now());
        if (!hasActiveSession) {
            throw new AccessDeniedException("Doctor does not have an active 24-hour consultation session for this patient. Please unlock the vault with a valid patient access PIN.");
        }

        ClinicalEncounter encounter = ClinicalEncounter.builder()
                .patient(patient)
                .doctor(doctor)
                .chiefComplaint(request.getChiefComplaint() != null ? request.getChiefComplaint().trim() : null)
                .diagnosis(request.getDiagnosis().trim())
                .clinicalNotes(request.getClinicalNotes().trim())
                .encounterDate(request.getEncounterDate())
                .build();

        encounter = clinicalEncounterRepository.save(encounter);

        List<HealthConditionResponse> addedConditions = new ArrayList<>();
        if (request.getConditionsToAdd() != null && !request.getConditionsToAdd().isEmpty()) {
            for (DoctorConditionInput input : request.getConditionsToAdd()) {
                HealthCondition condition = HealthCondition.builder()
                        .patient(patient)
                        .title(input.getTitle().trim())
                        .type(input.getType())
                        .sourceType(SourceType.DOCTOR_VERIFIED) // Strictly DOCTOR_VERIFIED
                        .dateRecorded(request.getEncounterDate())
                        .build();

                condition = healthConditionRepository.save(condition);
                addedConditions.add(mapToHealthConditionResponse(condition));
            }
        }

        String doctorName = "Dr. " + doctor.getFirstName() + " " + doctor.getLastName();

        return ClinicalEncounterResponse.builder()
                .id(encounter.getId())
                .patientId(patient.getId())
                .doctorId(doctor.getId())
                .doctorName(doctorName)
                .doctorSpecialty(doctor.getSpecialty())
                .chiefComplaint(encounter.getChiefComplaint())
                .diagnosis(encounter.getDiagnosis())
                .clinicalNotes(encounter.getClinicalNotes())
                .encounterDate(encounter.getEncounterDate())
                .createdAt(encounter.getCreatedAt())
                .conditionsAdded(addedConditions)
                .build();
    }

    private HealthConditionResponse mapToHealthConditionResponse(HealthCondition condition) {
        return HealthConditionResponse.builder()
                .id(condition.getId())
                .title(condition.getTitle())
                .type(condition.getType())
                .sourceType(condition.getSourceType())
                .dateRecorded(condition.getDateRecorded())
                .createdAt(condition.getCreatedAt())
                .build();
    }

    private ClinicalEncounterSummaryDto mapToClinicalEncounterSummary(ClinicalEncounter encounter) {
        String docName = "Dr. " + encounter.getDoctor().getFirstName() + " " + encounter.getDoctor().getLastName();
        return ClinicalEncounterSummaryDto.builder()
                .id(encounter.getId())
                .doctorId(encounter.getDoctor().getId())
                .doctorName(docName)
                .doctorSpecialty(encounter.getDoctor().getSpecialty())
                .encounterDate(encounter.getEncounterDate())
                .diagnosis(encounter.getDiagnosis())
                .clinicalNotes(encounter.getClinicalNotes())
                .createdAt(encounter.getCreatedAt())
                .build();
    }

    private PrescriptionResponse mapToPrescriptionResponse(Prescription p) {
        String docName = "Dr. " + p.getDoctor().getFirstName() + " " + p.getDoctor().getLastName();
        return PrescriptionResponse.builder()
                .id(p.getId())
                .patientId(p.getPatient().getId())
                .doctorId(p.getDoctor().getId())
                .doctorName(docName)
                .encounterId(p.getEncounter() != null ? p.getEncounter().getId() : null)
                .medicationName(p.getMedicationName())
                .dosage(p.getDosage())
                .instructions(p.getInstructions())
                .status(p.getStatus())
                .issuedAt(p.getIssuedAt())
                .expiresAt(p.getExpiresAt())
                .build();
    }

    private LabTestResultResponse mapToLabTestResultResponse(LabTestResult r) {
        String docName = "Dr. " + r.getDoctor().getFirstName() + " " + r.getDoctor().getLastName();
        return LabTestResultResponse.builder()
                .id(r.getId())
                .patientId(r.getPatient().getId())
                .doctorId(r.getDoctor().getId())
                .doctorName(docName)
                .testName(r.getTestName())
                .numericValue(r.getNumericValue())
                .unit(r.getUnit())
                .recordedAt(r.getRecordedAt())
                .createdAt(r.getCreatedAt())
                .build();
    }
}

package com.apollo.service.impl;

import com.apollo.domain.entity.ClinicalEncounter;
import com.apollo.domain.entity.DoctorProfile;
import com.apollo.domain.entity.PatientProfile;
import com.apollo.domain.entity.Prescription;
import com.apollo.domain.enums.DoctorRole;
import com.apollo.domain.enums.PrescriptionStatus;
import com.apollo.domain.enums.Role;
import com.apollo.dto.prescription.CreatePrescriptionRequest;
import com.apollo.dto.prescription.PrescriptionResponse;
import com.apollo.dto.prescription.UpdatePrescriptionStatusRequest;
import com.apollo.exception.ResourceNotFoundException;
import com.apollo.repository.ActiveVaultSessionRepository;
import com.apollo.repository.ClinicalEncounterRepository;
import com.apollo.repository.DoctorProfileRepository;
import com.apollo.repository.PatientProfileRepository;
import com.apollo.repository.PrescriptionRepository;
import com.apollo.service.PrescriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionServiceImpl implements PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final ClinicalEncounterRepository clinicalEncounterRepository;
    private final ActiveVaultSessionRepository activeVaultSessionRepository;

    @Override
    @Transactional
    public PrescriptionResponse issuePrescription(UUID doctorProfileId, CreatePrescriptionRequest request) {
        DoctorProfile doctor = doctorProfileRepository.findById(doctorProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found: " + doctorProfileId));

        // Role restriction: Only GPs and Specialists can issue prescriptions
        if (doctor.getDoctorRole() != DoctorRole.GENERAL_PRACTITIONER && doctor.getDoctorRole() != DoctorRole.SPECIALIST) {
            throw new AccessDeniedException("Only general practitioners and specialists are authorized to issue prescriptions.");
        }

        PatientProfile patient = patientProfileRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found: " + request.getPatientId()));

        // Active 24-hour consultation session validation
        boolean hasActiveSession = activeVaultSessionRepository
                .existsByDoctorIdAndPatientIdAndExpiresAtAfter(doctor.getId(), patient.getId(), Instant.now());
        if (!hasActiveSession) {
            throw new AccessDeniedException("Doctor does not have an active 24-hour consultation session for this patient. Please unlock the vault with a valid patient access PIN.");
        }

        ClinicalEncounter encounter = null;
        if (request.getEncounterId() != null) {
            encounter = clinicalEncounterRepository.findById(request.getEncounterId())
                    .orElseThrow(() -> new ResourceNotFoundException("Clinical encounter not found: " + request.getEncounterId()));

            if (!encounter.getPatient().getId().equals(patient.getId())) {
                throw new IllegalArgumentException("Clinical encounter does not belong to the specified patient.");
            }
        }

        Prescription prescription = Prescription.builder()
                .patient(patient)
                .doctor(doctor)
                .encounter(encounter)
                .medicationName(request.getMedicationName().trim())
                .dosage(request.getDosage().trim())
                .instructions(request.getInstructions().trim())
                .status(PrescriptionStatus.ACTIVE)
                .issuedAt(Instant.now())
                .expiresAt(request.getExpiresAt())
                .build();

        Prescription saved = prescriptionRepository.save(prescription);
        log.info("Prescription issued successfully: id={}, patientId={}, doctorId={}, medication={}",
                saved.getId(), patient.getId(), doctor.getId(), saved.getMedicationName());

        return mapToPrescriptionResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PrescriptionResponse> getPatientPrescriptions(UUID patientProfileId, PrescriptionStatus status) {
        patientProfileRepository.findById(patientProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found: " + patientProfileId));

        List<Prescription> prescriptions;
        if (status != null) {
            prescriptions = prescriptionRepository.findByPatientIdAndStatusOrderByIssuedAtDesc(patientProfileId, status);
        } else {
            prescriptions = prescriptionRepository.findByPatientIdOrderByIssuedAtDesc(patientProfileId);
        }

        return prescriptions.stream()
                .map(this::mapToPrescriptionResponse)
                .toList();
    }

    @Override
    @Transactional
    public PrescriptionResponse updateStatus(UUID profileId, Role role, UUID prescriptionId, UpdatePrescriptionStatusRequest request) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found: " + prescriptionId));

        if (role == Role.ROLE_PATIENT) {
            if (!prescription.getPatient().getId().equals(profileId)) {
                throw new AccessDeniedException("You do not have permission to update this prescription.");
            }
            if (request.getStatus() != PrescriptionStatus.FULFILLED) {
                throw new IllegalArgumentException("Patients can only update prescription status to FULFILLED.");
            }
        } else if (role == Role.ROLE_DOCTOR) {
            DoctorProfile doctor = doctorProfileRepository.findById(profileId)
                    .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found: " + profileId));

            if (doctor.getDoctorRole() == DoctorRole.PHARMACIST) {
                // Pharmacists can mark active prescriptions as FULFILLED upon dispensing
                if (request.getStatus() != PrescriptionStatus.FULFILLED) {
                    throw new IllegalArgumentException("Pharmacists can only mark prescriptions as FULFILLED.");
                }
            } else {
                // Prescribing doctor can mark as CANCELLED
                if (!prescription.getDoctor().getId().equals(profileId)) {
                    throw new AccessDeniedException("You do not have permission to update this prescription.");
                }
                if (request.getStatus() == PrescriptionStatus.FULFILLED) {
                    throw new IllegalArgumentException("Doctors cannot mark prescriptions as FULFILLED. Fulfillment is recorded by the patient upon pharmacy dispensing.");
                }
            }
        } else {
            throw new AccessDeniedException("Unauthorized role for updating prescription status.");
        }

        if (prescription.getStatus() == PrescriptionStatus.CANCELLED) {
            throw new IllegalStateException("Cannot update a prescription that has already been CANCELLED.");
        }
        if (prescription.getStatus() == PrescriptionStatus.FULFILLED) {
            throw new IllegalStateException("Cannot update a prescription that has already been FULFILLED.");
        }

        prescription.setStatus(request.getStatus());
        Prescription updated = prescriptionRepository.save(prescription);
        log.info("Prescription status updated: id={}, newStatus={}", updated.getId(), updated.getStatus());

        return mapToPrescriptionResponse(updated);
    }

    private PrescriptionResponse mapToPrescriptionResponse(Prescription prescription) {
        String doctorName = "Dr. " + prescription.getDoctor().getFirstName() + " " + prescription.getDoctor().getLastName();
        return PrescriptionResponse.builder()
                .id(prescription.getId())
                .patientId(prescription.getPatient().getId())
                .doctorId(prescription.getDoctor().getId())
                .doctorName(doctorName)
                .encounterId(prescription.getEncounter() != null ? prescription.getEncounter().getId() : null)
                .medicationName(prescription.getMedicationName())
                .dosage(prescription.getDosage())
                .instructions(prescription.getInstructions())
                .status(prescription.getStatus())
                .issuedAt(prescription.getIssuedAt())
                .expiresAt(prescription.getExpiresAt())
                .build();
    }
}

package com.apollo.service.impl;

import com.apollo.domain.entity.ClinicalEncounter;
import com.apollo.domain.entity.DoctorProfile;
import com.apollo.domain.entity.PatientProfile;
import com.apollo.domain.entity.Prescription;
import com.apollo.domain.enums.DoctorRole;
import com.apollo.domain.enums.PrescriptionStatus;
import com.apollo.domain.enums.Role;
import com.apollo.domain.enums.SourceType;
import com.apollo.dto.prescription.CreatePrescriptionRequest;
import com.apollo.dto.prescription.PrescriptionResponse;
import com.apollo.dto.prescription.UpdatePrescriptionStatusRequest;
import com.apollo.dto.vault.PatientPrescriptionRequest;
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
                .source(SourceType.DOCTOR_VERIFIED)
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
            if (prescription.getSource() == SourceType.PATIENT_DECLARED) {
                if (request.getStatus() != PrescriptionStatus.FULFILLED && request.getStatus() != PrescriptionStatus.ACTIVE) {
                    throw new IllegalArgumentException("Patients can only update prescription status to ACTIVE or FULFILLED.");
                }
            } else {
                if (request.getStatus() != PrescriptionStatus.FULFILLED) {
                    throw new IllegalArgumentException("Patients can only update prescription status to FULFILLED.");
                }
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
                if (prescription.getDoctor() == null || !prescription.getDoctor().getId().equals(profileId)) {
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
        if (prescription.getStatus() == PrescriptionStatus.FULFILLED && prescription.getSource() != SourceType.PATIENT_DECLARED) {
            throw new IllegalStateException("Cannot update a prescription that has already been FULFILLED.");
        }

        prescription.setStatus(request.getStatus());
        Prescription updated = prescriptionRepository.save(prescription);
        log.info("Prescription status updated: id={}, newStatus={}", updated.getId(), updated.getStatus());

        return mapToPrescriptionResponse(updated);
    }

    @Override
    @Transactional
    public PrescriptionResponse addPatientPrescription(UUID patientProfileId, PatientPrescriptionRequest request) {
        PatientProfile patient = patientProfileRepository.findById(patientProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found: " + patientProfileId));

        Prescription prescription = Prescription.builder()
                .patient(patient)
                .doctor(null)
                .encounter(null)
                .medicationName(request.getMedicationName().trim())
                .dosage(request.getDosage() != null && !request.getDosage().isBlank() ? request.getDosage().trim() : null)
                .frequency(request.getFrequency() != null && !request.getFrequency().isBlank() ? request.getFrequency().trim() : null)
                .instructions(request.getInstructions() != null && !request.getInstructions().isBlank() ? request.getInstructions().trim() : null)
                .status(PrescriptionStatus.ACTIVE)
                .source(SourceType.PATIENT_DECLARED)
                .issuedAt(request.getIssuedAt() != null ? request.getIssuedAt() : Instant.now())
                .expiresAt(null)
                .build();

        Prescription saved = prescriptionRepository.save(prescription);
        log.info("Patient prescription added successfully: id={}, patientId={}, medication={}",
                saved.getId(), patient.getId(), saved.getMedicationName());

        return mapToPrescriptionResponse(saved);
    }

    @Override
    @Transactional
    public PrescriptionResponse updatePatientPrescription(UUID patientProfileId, UUID prescriptionId, PatientPrescriptionRequest request) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found: " + prescriptionId));

        if (!prescription.getPatient().getId().equals(patientProfileId)) {
            throw new AccessDeniedException("You do not have permission to update this prescription.");
        }

        if (prescription.getSource() != SourceType.PATIENT_DECLARED) {
            throw new AccessDeniedException("Doctor-verified prescriptions cannot be edited by the patient.");
        }

        prescription.setMedicationName(request.getMedicationName().trim());
        prescription.setDosage(request.getDosage() != null && !request.getDosage().isBlank() ? request.getDosage().trim() : null);
        prescription.setFrequency(request.getFrequency() != null && !request.getFrequency().isBlank() ? request.getFrequency().trim() : null);
        prescription.setInstructions(request.getInstructions() != null && !request.getInstructions().isBlank() ? request.getInstructions().trim() : null);
        if (request.getIssuedAt() != null) {
            prescription.setIssuedAt(request.getIssuedAt());
        }

        Prescription saved = prescriptionRepository.save(prescription);
        log.info("Patient prescription updated successfully: id={}, patientId={}", saved.getId(), patientProfileId);

        return mapToPrescriptionResponse(saved);
    }

    @Override
    @Transactional
    public void deletePatientPrescription(UUID patientProfileId, UUID prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found: " + prescriptionId));

        if (!prescription.getPatient().getId().equals(patientProfileId)) {
            throw new AccessDeniedException("You do not have permission to delete this prescription.");
        }

        if (prescription.getSource() != SourceType.PATIENT_DECLARED) {
            throw new AccessDeniedException("Doctor-verified prescriptions cannot be deleted by the patient.");
        }

        prescriptionRepository.delete(prescription);
        log.info("Patient prescription deleted successfully: id={}, patientId={}", prescriptionId, patientProfileId);
    }

    private PrescriptionResponse mapToPrescriptionResponse(Prescription prescription) {
        DoctorProfile doc = prescription.getDoctor();
        String doctorName = doc != null ? "Dr. " + doc.getFirstName() + " " + doc.getLastName() : null;
        return PrescriptionResponse.builder()
                .id(prescription.getId())
                .patientId(prescription.getPatient().getId())
                .doctorId(doc != null ? doc.getId() : null)
                .doctorName(doctorName)
                .encounterId(prescription.getEncounter() != null ? prescription.getEncounter().getId() : null)
                .medicationName(prescription.getMedicationName())
                .dosage(prescription.getDosage())
                .frequency(prescription.getFrequency())
                .instructions(prescription.getInstructions())
                .status(prescription.getStatus())
                .source(prescription.getSource() != null ? prescription.getSource() : SourceType.DOCTOR_VERIFIED)
                .issuedAt(prescription.getIssuedAt())
                .expiresAt(prescription.getExpiresAt())
                .build();
    }
}

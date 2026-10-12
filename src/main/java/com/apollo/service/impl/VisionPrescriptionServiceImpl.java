package com.apollo.service.impl;

import com.apollo.domain.entity.ClinicalEncounter;
import com.apollo.domain.entity.DoctorProfile;
import com.apollo.domain.entity.PatientProfile;
import com.apollo.domain.entity.VisionEyeValues;
import com.apollo.domain.entity.VisionPrescription;
import com.apollo.domain.enums.DoctorRole;
import com.apollo.dto.vision.CreateVisionPrescriptionRequest;
import com.apollo.dto.vision.VisionEyeResponse;
import com.apollo.dto.vision.VisionPrescriptionResponse;
import com.apollo.exception.ResourceNotFoundException;
import com.apollo.repository.ActiveVaultSessionRepository;
import com.apollo.repository.ClinicalEncounterRepository;
import com.apollo.repository.DoctorProfileRepository;
import com.apollo.repository.PatientProfileRepository;
import com.apollo.repository.VisionPrescriptionRepository;
import com.apollo.service.VisionPrescriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisionPrescriptionServiceImpl implements VisionPrescriptionService {

    private final VisionPrescriptionRepository visionPrescriptionRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final ClinicalEncounterRepository clinicalEncounterRepository;
    private final ActiveVaultSessionRepository activeVaultSessionRepository;

    @Override
    @Transactional
    public VisionPrescriptionResponse issueVisionPrescription(UUID doctorProfileId, CreateVisionPrescriptionRequest request) {
        DoctorProfile doctor = doctorProfileRepository.findById(doctorProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found: " + doctorProfileId));

        // Role restriction: Only GPs and Specialists can issue glasses prescriptions
        if (doctor.getDoctorRole() != DoctorRole.GENERAL_PRACTITIONER && doctor.getDoctorRole() != DoctorRole.SPECIALIST) {
            throw new AccessDeniedException("Only general practitioners and specialists are authorized to issue glasses prescriptions.");
        }

        // Shape and range validation needs no database access, so it runs before the patient and session lookups.
        if (request.getPatientId() == null) {
            throw new IllegalArgumentException("Patient ID is required.");
        }
        VisionEyeValues rightEye = VisionPrescriptionValidator.normaliseEye(request.getRightEye());
        VisionEyeValues leftEye = VisionPrescriptionValidator.normaliseEye(request.getLeftEye());
        if (rightEye == null && leftEye == null) {
            throw new IllegalArgumentException("Enter the prescription for at least one eye.");
        }
        BigDecimal pupillaryDistance = VisionPrescriptionValidator.normalisePupillaryDistance(request.getPupillaryDistanceMm());
        String notes = VisionPrescriptionValidator.normaliseNotes(request.getNotes());

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

        VisionPrescription saved = visionPrescriptionRepository.save(VisionPrescription.builder()
                .patient(patient)
                .doctor(doctor)
                .encounter(encounter)
                .rightEye(rightEye)
                .leftEye(leftEye)
                .pupillaryDistanceMm(pupillaryDistance)
                .notes(notes)
                .issuedAt(Instant.now())
                .expiresAt(request.getExpiresAt())
                .build());

        log.info("Vision prescription issued: id={}, patientId={}, doctorId={}",
                saved.getId(), patient.getId(), doctor.getId());

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VisionPrescriptionResponse> getVisionPrescriptions(UUID patientProfileId) {
        return visionPrescriptionRepository.findByPatientIdOrderByIssuedAtDescCreatedAtDesc(patientProfileId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    private VisionPrescriptionResponse mapToResponse(VisionPrescription p) {
        DoctorProfile doctor = p.getDoctor();
        String doctorName = "Dr. " + doctor.getFirstName() + " " + doctor.getLastName();
        return VisionPrescriptionResponse.builder()
                .id(p.getId())
                .patientId(p.getPatient().getId())
                .doctorId(doctor.getId())
                .doctorName(doctorName)
                .encounterId(p.getEncounter() != null ? p.getEncounter().getId() : null)
                .rightEye(mapEye(p.getRightEye()))
                .leftEye(mapEye(p.getLeftEye()))
                .pupillaryDistanceMm(p.getPupillaryDistanceMm())
                .notes(p.getNotes())
                .issuedAt(p.getIssuedAt())
                .expiresAt(p.getExpiresAt())
                .build();
    }

    /** Hibernate leaves an embedded object null when all of its columns are null; the API always returns an object. */
    private static VisionEyeResponse mapEye(VisionEyeValues eye) {
        if (eye == null) {
            return new VisionEyeResponse();
        }
        return VisionEyeResponse.builder()
                .sphere(eye.getSphere())
                .cylinder(eye.getCylinder())
                .axis(eye.getAxis() != null ? eye.getAxis().intValue() : null)
                .add(eye.getAddPower())
                .build();
    }
}

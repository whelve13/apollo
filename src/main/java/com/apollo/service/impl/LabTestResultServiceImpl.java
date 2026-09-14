package com.apollo.service.impl;

import com.apollo.domain.entity.DoctorProfile;
import com.apollo.domain.entity.LabTestResult;
import com.apollo.domain.entity.PatientProfile;
import com.apollo.domain.enums.DoctorRole;
import com.apollo.dto.lab.CreateLabTestResultRequest;
import com.apollo.dto.lab.LabTestResultResponse;
import com.apollo.exception.ResourceNotFoundException;
import com.apollo.repository.ActiveVaultSessionRepository;
import com.apollo.repository.DoctorProfileRepository;
import com.apollo.repository.LabTestResultRepository;
import com.apollo.repository.PatientProfileRepository;
import com.apollo.service.LabTestResultService;
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
public class LabTestResultServiceImpl implements LabTestResultService {

    private final LabTestResultRepository labTestResultRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final ActiveVaultSessionRepository activeVaultSessionRepository;

    @Override
    @Transactional
    public LabTestResultResponse recordTestResult(UUID doctorProfileId, CreateLabTestResultRequest request) {
        DoctorProfile doctor = doctorProfileRepository.findById(doctorProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found: " + doctorProfileId));

        // Pharmacists cannot record lab test results
        if (doctor.getDoctorRole() == DoctorRole.PHARMACIST) {
            throw new AccessDeniedException("Pharmacists are not authorized to record lab test results.");
        }

        PatientProfile patient = patientProfileRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found: " + request.getPatientId()));

        // Active 24-hour consultation session validation
        boolean hasActiveSession = activeVaultSessionRepository
                .existsByDoctorIdAndPatientIdAndExpiresAtAfter(doctor.getId(), patient.getId(), Instant.now());
        if (!hasActiveSession) {
            throw new AccessDeniedException("Doctor does not have an active 24-hour consultation session for this patient. Please unlock the vault with a valid patient access PIN.");
        }

        LabTestResult result = LabTestResult.builder()
                .patient(patient)
                .doctor(doctor)
                .testName(request.getTestName().trim())
                .numericValue(request.getNumericValue())
                .unit(request.getUnit().trim())
                .recordedAt(request.getRecordedAt())
                .build();

        LabTestResult saved = labTestResultRepository.save(result);
        log.info("Lab test result recorded: id={}, testName={}, numericValue={}, patientId={}",
                saved.getId(), saved.getTestName(), saved.getNumericValue(), patient.getId());

        return mapToLabTestResultResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LabTestResultResponse> getPatientTestResults(UUID patientProfileId, String testName) {
        patientProfileRepository.findById(patientProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found: " + patientProfileId));

        List<LabTestResult> results;
        if (testName != null && !testName.isBlank()) {
            results = labTestResultRepository
                    .findByPatientIdAndTestNameIgnoreCaseOrderByRecordedAtAscCreatedAtAsc(patientProfileId, testName.trim());
        } else {
            results = labTestResultRepository
                    .findByPatientIdOrderByRecordedAtAscCreatedAtAsc(patientProfileId);
        }

        return results.stream()
                .map(this::mapToLabTestResultResponse)
                .toList();
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

package com.apollo.service.impl;

import com.apollo.domain.entity.DoctorProfile;
import com.apollo.domain.entity.LabTestResult;
import com.apollo.domain.entity.PatientProfile;
import com.apollo.domain.enums.DoctorRole;
import com.apollo.domain.enums.LabResultSource;
import com.apollo.domain.enums.LabResultStatus;
import com.apollo.dto.lab.CreateLabTestPanelRequest;
import com.apollo.dto.lab.CreateLabTestResultRequest;
import com.apollo.dto.lab.LabResultValueRequest;
import com.apollo.dto.lab.LabTestPanelRequest;
import com.apollo.dto.lab.LabTestPanelResponse;
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
        DoctorProfile doctor = loadDoctorAllowedToRecord(doctorProfileId);
        PatientProfile patient = loadPatientWithActiveSession(doctor, request.getPatientId());

        LabTestResult result = baseBuilder(patient, doctor, LabResultSource.DOCTOR_VERIFIED,
                statusOrFinal(request.getStatus()), request.getRecordedAt(), request)
                .build();

        LabTestResult saved = labTestResultRepository.save(result);
        log.info("Lab test result recorded: id={}, testName={}, patientId={}",
                saved.getId(), saved.getTestName(), patient.getId());

        return mapToLabTestResultResponse(saved);
    }

    @Override
    @Transactional
    public LabTestPanelResponse recordTestPanel(UUID doctorProfileId, CreateLabTestPanelRequest request) {
        DoctorProfile doctor = loadDoctorAllowedToRecord(doctorProfileId);
        PatientProfile patient = loadPatientWithActiveSession(doctor, request.getPatientId());

        return savePanel(patient, doctor, LabResultSource.DOCTOR_VERIFIED, statusOrFinal(request.getStatus()), request);
    }

    @Override
    @Transactional
    public LabTestPanelResponse recordPatientTestPanel(UUID patientProfileId, LabTestPanelRequest request) {
        PatientProfile patient = patientProfileRepository.findById(patientProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found: " + patientProfileId));

        // A patient draft would be visible to nobody, so it is rejected instead of silently saved as final.
        if (request.getStatus() == LabResultStatus.DRAFT) {
            throw new IllegalArgumentException("Patient-reported lab results cannot be saved as drafts.");
        }

        return savePanel(patient, null, LabResultSource.PATIENT_REPORTED, LabResultStatus.FINAL, request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LabTestResultResponse> getPatientTestResults(UUID patientProfileId, String testName) {
        patientProfileRepository.findById(patientProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found: " + patientProfileId));

        List<LabTestResult> results;
        if (testName != null && !testName.isBlank()) {
            results = labTestResultRepository
                    .findByPatientIdAndStatusAndTestNameIgnoreCaseOrderByRecordedAtAscCreatedAtAsc(
                            patientProfileId, LabResultStatus.FINAL, testName.trim());
        } else {
            results = labTestResultRepository
                    .findByPatientIdAndStatusOrderByRecordedAtAscCreatedAtAsc(patientProfileId, LabResultStatus.FINAL);
        }

        return results.stream()
                .map(this::mapToLabTestResultResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LabTestResultResponse> getTestResultsForDoctor(UUID patientProfileId, UUID doctorProfileId) {
        return labTestResultRepository.findVisibleToDoctor(patientProfileId, doctorProfileId).stream()
                .map(this::mapToLabTestResultResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deletePatientTestResult(UUID patientProfileId, UUID testResultId) {
        LabTestResult result = labTestResultRepository.findById(testResultId)
                .orElseThrow(() -> new ResourceNotFoundException("Lab test result not found with id: " + testResultId));

        // Drafts are only visible to their author, so a patient gets the same 404 as for a foreign record.
        if (!result.getPatient().getId().equals(patientProfileId) || result.getStatus() != LabResultStatus.FINAL) {
            throw new ResourceNotFoundException("Lab test result not found with id: " + testResultId);
        }

        if (result.getSource() != LabResultSource.PATIENT_REPORTED) {
            throw new AccessDeniedException("Patients cannot delete doctor-verified lab results.");
        }

        labTestResultRepository.delete(result);
    }

    private DoctorProfile loadDoctorAllowedToRecord(UUID doctorProfileId) {
        DoctorProfile doctor = doctorProfileRepository.findById(doctorProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found: " + doctorProfileId));

        // Pharmacists cannot record lab test results
        if (doctor.getDoctorRole() == DoctorRole.PHARMACIST) {
            throw new AccessDeniedException("Pharmacists are not authorized to record lab test results.");
        }
        return doctor;
    }

    private PatientProfile loadPatientWithActiveSession(DoctorProfile doctor, UUID patientId) {
        PatientProfile patient = patientProfileRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found: " + patientId));

        // Active PIN-backed consultation session validation
        boolean hasActiveSession = activeVaultSessionRepository
                .existsByDoctorIdAndPatientIdAndExpiresAtAfter(doctor.getId(), patient.getId(), Instant.now());
        if (!hasActiveSession) {
            throw new AccessDeniedException("Doctor does not have an active PIN-backed consultation session for this patient. Please unlock the vault with a valid patient access PIN.");
        }
        return patient;
    }

    private LabTestPanelResponse savePanel(PatientProfile patient, DoctorProfile doctor, LabResultSource source,
                                           LabResultStatus status, LabTestPanelRequest request) {
        UUID panelId = UUID.randomUUID();
        String templateCode = trimToNull(request.getTemplateCode());

        List<LabTestResult> results = request.getEntries().stream()
                .map(entry -> baseBuilder(patient, doctor, source, status, request.getRecordedAt(), entry)
                        .panelId(panelId)
                        .templateCode(templateCode)
                        .templateVersion(request.getTemplateVersion())
                        .entryKey(trimToNull(entry.getKey()))
                        .build())
                .toList();

        List<LabTestResult> saved = labTestResultRepository.saveAll(results);
        log.info("Lab test panel recorded: panelId={}, entries={}, source={}, patientId={}",
                panelId, saved.size(), source, patient.getId());

        return LabTestPanelResponse.builder()
                .panelId(panelId)
                .patientId(patient.getId())
                .recordedAt(request.getRecordedAt())
                .status(status)
                .templateCode(templateCode)
                .templateVersion(request.getTemplateVersion())
                .entries(saved.stream().map(this::mapToLabTestResultResponse).toList())
                .build();
    }

    private LabTestResult.LabTestResultBuilder baseBuilder(PatientProfile patient, DoctorProfile doctor,
                                                           LabResultSource source, LabResultStatus status,
                                                           Instant recordedAt, LabResultValueRequest value) {
        return LabTestResult.builder()
                .patient(patient)
                .doctor(doctor)
                .source(source)
                .status(status)
                .recordedAt(recordedAt)
                .testName(value.getTestName().trim())
                .numericValue(value.getNumericValue())
                .valueText(trimToNull(value.getValueText()))
                .qualifier(value.getQualifier())
                .unit(value.getUnit() != null ? value.getUnit().trim() : "")
                .referenceLow(value.getReferenceLow())
                .referenceHigh(value.getReferenceHigh())
                .flag(value.getFlag())
                .comment(trimToNull(value.getComment()));
    }

    private static LabResultStatus statusOrFinal(LabResultStatus status) {
        return status != null ? status : LabResultStatus.FINAL;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private LabTestResultResponse mapToLabTestResultResponse(LabTestResult r) {
        DoctorProfile doctor = r.getDoctor();
        String docName = doctor != null ? "Dr. " + doctor.getFirstName() + " " + doctor.getLastName() : null;
        return LabTestResultResponse.builder()
                .id(r.getId())
                .patientId(r.getPatient().getId())
                .doctorId(doctor != null ? doctor.getId() : null)
                .doctorName(docName)
                .testName(r.getTestName())
                .numericValue(r.getNumericValue())
                .valueText(r.getValueText())
                .qualifier(r.getQualifier())
                .unit(r.getUnit())
                .referenceLow(r.getReferenceLow())
                .referenceHigh(r.getReferenceHigh())
                .flag(r.getFlag())
                .comment(r.getComment())
                .status(r.getStatus())
                .source(r.getSource())
                .panelId(r.getPanelId())
                .templateCode(r.getTemplateCode())
                .templateVersion(r.getTemplateVersion())
                .key(r.getEntryKey())
                .recordedAt(r.getRecordedAt())
                .createdAt(r.getCreatedAt())
                .build();
    }
}

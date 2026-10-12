package com.apollo.service;

import com.apollo.dto.lab.CreateLabTestPanelRequest;
import com.apollo.dto.lab.CreateLabTestResultRequest;
import com.apollo.dto.lab.LabTestPanelRequest;
import com.apollo.dto.lab.LabTestPanelResponse;
import com.apollo.dto.lab.LabTestResultResponse;

import java.util.List;
import java.util.UUID;

public interface LabTestResultService {

    LabTestResultResponse recordTestResult(UUID doctorProfileId, CreateLabTestResultRequest request);

    /** Saves every entry of the panel in one transaction. */
    LabTestPanelResponse recordTestPanel(UUID doctorProfileId, CreateLabTestPanelRequest request);

    /** Saves a patient-reported panel; always stored as FINAL with source PATIENT_REPORTED. */
    LabTestPanelResponse recordPatientTestPanel(UUID patientProfileId, LabTestPanelRequest request);

    /** FINAL results only, ordered by recordedAt ascending. */
    List<LabTestResultResponse> getPatientTestResults(UUID patientProfileId, String testName);

    /** FINAL results plus the clinician's own drafts, ordered by recordedAt ascending. */
    List<LabTestResultResponse> getTestResultsForDoctor(UUID patientProfileId, UUID doctorProfileId);

    void deletePatientTestResult(UUID patientProfileId, UUID testResultId);
}

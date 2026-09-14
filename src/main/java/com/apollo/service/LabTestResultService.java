package com.apollo.service;

import com.apollo.dto.lab.CreateLabTestResultRequest;
import com.apollo.dto.lab.LabTestResultResponse;

import java.util.List;
import java.util.UUID;

public interface LabTestResultService {

    LabTestResultResponse recordTestResult(UUID doctorProfileId, CreateLabTestResultRequest request);

    List<LabTestResultResponse> getPatientTestResults(UUID patientProfileId, String testName);
}

package com.apollo.service;

import com.apollo.dto.patient.PatientProfileResponse;
import com.apollo.dto.patient.UpdatePatientProfileRequest;
import com.apollo.dto.vault.AccessGrantResponse;
import com.apollo.dto.vault.CreateHealthConditionRequest;
import com.apollo.dto.vault.HealthConditionResponse;
import com.apollo.dto.vault.PatientVaultTimelineResponse;

import java.util.List;
import java.util.UUID;

public interface PatientVaultService {

    HealthConditionResponse addCondition(UUID patientProfileId, CreateHealthConditionRequest request);

    List<HealthConditionResponse> getConditions(UUID patientProfileId);

    void deleteCondition(UUID patientProfileId, UUID conditionId);

    AccessGrantResponse generateAccessGrant(UUID patientProfileId);

    PatientVaultTimelineResponse getTimeline(UUID patientProfileId);

    PatientProfileResponse updateProfile(UUID patientProfileId, UpdatePatientProfileRequest request);

    PatientProfileResponse getProfile(UUID patientProfileId);
}

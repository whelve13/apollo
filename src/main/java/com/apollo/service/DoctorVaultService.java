package com.apollo.service;

import com.apollo.dto.doctor.ClinicalEncounterResponse;
import com.apollo.dto.doctor.CreateEncounterRequest;
import com.apollo.dto.doctor.UnlockVaultRequest;
import com.apollo.dto.doctor.UnlockedVaultResponse;

import java.util.UUID;
import java.util.List;

public interface DoctorVaultService {

    UnlockedVaultResponse unlockVault(UUID doctorProfileId, UnlockVaultRequest request);

    List<UnlockedVaultResponse> getActiveVaults(UUID doctorProfileId);

    UnlockedVaultResponse getActiveVault(UUID doctorProfileId, UUID patientId);

    void disconnectVault(UUID doctorProfileId, UUID patientId);

    ClinicalEncounterResponse createEncounter(UUID doctorProfileId, CreateEncounterRequest request);
}

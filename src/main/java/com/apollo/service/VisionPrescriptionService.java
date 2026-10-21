package com.apollo.service;

import com.apollo.dto.vision.CreateVisionPrescriptionRequest;
import com.apollo.dto.vision.VisionPrescriptionResponse;

import java.util.List;
import java.util.UUID;

public interface VisionPrescriptionService {

    /**
     * Issues a glasses prescription. Restricted to general practitioners and specialists holding an active
     * PIN-backed vault session for the patient.
     */
    VisionPrescriptionResponse issueVisionPrescription(UUID doctorProfileId, CreateVisionPrescriptionRequest request);

    /** All of the patient's glasses prescriptions, newest first. Callers are responsible for authorization. */
    List<VisionPrescriptionResponse> getVisionPrescriptions(UUID patientProfileId);
}

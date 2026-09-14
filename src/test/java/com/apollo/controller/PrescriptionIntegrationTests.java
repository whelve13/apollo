package com.apollo.controller;

import com.apollo.domain.entity.DoctorProfile;
import com.apollo.domain.entity.PatientProfile;
import com.apollo.domain.enums.HealthConditionType;
import com.apollo.domain.enums.PrescriptionStatus;
import com.apollo.dto.auth.RegisterDoctorRequest;
import com.apollo.dto.auth.RegisterPatientRequest;
import com.apollo.dto.doctor.CreateEncounterRequest;
import com.apollo.dto.doctor.UnlockVaultRequest;
import com.apollo.dto.prescription.CreatePrescriptionRequest;
import com.apollo.dto.prescription.UpdatePrescriptionStatusRequest;
import com.apollo.repository.AccessGrantRepository;
import com.apollo.repository.ActiveVaultSessionRepository;
import com.apollo.repository.ClinicalEncounterRepository;
import com.apollo.repository.DoctorProfileRepository;
import com.apollo.repository.HealthConditionRepository;
import com.apollo.repository.LabTestResultRepository;
import com.apollo.repository.PatientProfileRepository;
import com.apollo.repository.PrescriptionRepository;
import com.apollo.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrescriptionIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PatientProfileRepository patientProfileRepository;

    @Autowired
    private DoctorProfileRepository doctorProfileRepository;

    @Autowired
    private HealthConditionRepository healthConditionRepository;

    @Autowired
    private ClinicalEncounterRepository clinicalEncounterRepository;

    @Autowired
    private AccessGrantRepository accessGrantRepository;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private ActiveVaultSessionRepository activeVaultSessionRepository;

    @Autowired
    private LabTestResultRepository labTestResultRepository;

    @BeforeEach
    void setUp() {
        labTestResultRepository.deleteAll();
        prescriptionRepository.deleteAll();
        activeVaultSessionRepository.deleteAll();
        accessGrantRepository.deleteAll();
        clinicalEncounterRepository.deleteAll();
        healthConditionRepository.deleteAll();
        patientProfileRepository.deleteAll();
        doctorProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String registerAndGetToken(String email, String role) throws Exception {
        if ("PATIENT".equalsIgnoreCase(role)) {
            RegisterPatientRequest request = RegisterPatientRequest.builder()
                    .email(email)
                    .password("Password123!")
                    .firstName("John")
                    .lastName("Watson")
                    .dateOfBirth(LocalDate.of(1982, 7, 7))
                    .bloodType("O-")
                    .build();

            MvcResult result = mockMvc.perform(post("/api/v1/auth/register/patient")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn();

            return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
        } else {
            RegisterDoctorRequest request = RegisterDoctorRequest.builder()
                    .email(email)
                    .password("DoctorPass123!")
                    .firstName("Gregory")
                    .lastName("House")
                    .licenseNumber("MD-" + UUID.randomUUID().toString().substring(0, 8))
                    .specialty("Diagnostics")
                    .build();

            MvcResult result = mockMvc.perform(post("/api/v1/auth/register/doctor")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn();

            return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
        }
    }

    private PatientProfile getPatientProfile(String token) throws Exception {
        MvcResult meResult = mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        UUID profileId = UUID.fromString(objectMapper.readTree(meResult.getResponse().getContentAsString()).get("profileId").asText());
        return patientProfileRepository.findById(profileId).orElseThrow();
    }

    private DoctorProfile getDoctorProfile(String token) throws Exception {
        MvcResult meResult = mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        UUID profileId = UUID.fromString(objectMapper.readTree(meResult.getResponse().getContentAsString()).get("profileId").asText());
        return doctorProfileRepository.findById(profileId).orElseThrow();
    }

    private void unlockVaultSession(String patientToken, String doctorToken) throws Exception {
        MvcResult grantResult = mockMvc.perform(post("/api/v1/patient/vault/access-grants")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isCreated())
                .andReturn();
        String accessCode = objectMapper.readTree(grantResult.getResponse().getContentAsString()).get("accessCode").asText();

        mockMvc.perform(post("/api/v1/doctor/vault/unlock")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UnlockVaultRequest.builder().accessCode(accessCode).build())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Doctor should successfully issue a standalone prescription (without encounter)")
    void testDoctorIssuesPrescriptionStandalone() throws Exception {
        String patientToken = registerAndGetToken("patient.presc1@apollo.local", "PATIENT");
        String doctorToken = registerAndGetToken("doctor.presc1@apollo.local", "DOCTOR");

        PatientProfile patient = getPatientProfile(patientToken);
        DoctorProfile doctor = getDoctorProfile(doctorToken);

        unlockVaultSession(patientToken, doctorToken);

        CreatePrescriptionRequest request = CreatePrescriptionRequest.builder()
                .patientId(patient.getId())
                .encounterId(null)
                .medicationName("Amoxicillin 500mg")
                .dosage("1 capsule TID for 7 days")
                .instructions("Take with water after meals. Complete full course.")
                .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .build();

        mockMvc.perform(post("/api/v1/doctor/prescriptions")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.patientId", is(patient.getId().toString())))
                .andExpect(jsonPath("$.doctorId", is(doctor.getId().toString())))
                .andExpect(jsonPath("$.doctorName", is("Dr. Gregory House")))
                .andExpect(jsonPath("$.encounterId", nullValue()))
                .andExpect(jsonPath("$.medicationName", is("Amoxicillin 500mg")))
                .andExpect(jsonPath("$.dosage", is("1 capsule TID for 7 days")))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.issuedAt", notNullValue()))
                .andExpect(jsonPath("$.expiresAt", notNullValue()));

        assertThat(prescriptionRepository.findByPatientIdOrderByIssuedAtDesc(patient.getId())).hasSize(1);
    }

    @Test
    @DisplayName("Doctor should successfully issue an encounter-linked prescription")
    void testDoctorIssuesPrescriptionWithEncounter() throws Exception {
        String patientToken = registerAndGetToken("patient.presc2@apollo.local", "PATIENT");
        String doctorToken = registerAndGetToken("doctor.presc2@apollo.local", "DOCTOR");

        PatientProfile patient = getPatientProfile(patientToken);

        // 1. Patient generates access grant PIN
        MvcResult grantResult = mockMvc.perform(post("/api/v1/patient/vault/access-grants")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isCreated())
                .andReturn();
        String accessCode = objectMapper.readTree(grantResult.getResponse().getContentAsString()).get("accessCode").asText();

        // 2. Doctor unlocks vault
        UnlockVaultRequest unlockRequest = UnlockVaultRequest.builder().accessCode(accessCode).build();
        mockMvc.perform(post("/api/v1/doctor/vault/unlock")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unlockRequest)))
                .andExpect(status().isOk());

        // 3. Doctor logs encounter
        CreateEncounterRequest encRequest = CreateEncounterRequest.builder()
                .patientId(patient.getId())
                .chiefComplaint("Bacterial sinusitis")
                .diagnosis("Acute bacterial rhinosinusitis")
                .clinicalNotes("Prescribed antibiotics and nasal corticosteroids.")
                .encounterDate(LocalDate.now())
                .build();

        MvcResult encResult = mockMvc.perform(post("/api/v1/doctor/encounters")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(encRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID encounterId = UUID.fromString(objectMapper.readTree(encResult.getResponse().getContentAsString()).get("id").asText());

        // 4. Doctor issues prescription linked to this encounter
        CreatePrescriptionRequest prescRequest = CreatePrescriptionRequest.builder()
                .patientId(patient.getId())
                .encounterId(encounterId)
                .medicationName("Augmentin 875/125mg")
                .dosage("1 tablet twice daily")
                .instructions("Take with food for 10 days.")
                .expiresAt(Instant.now().plus(14, ChronoUnit.DAYS))
                .build();

        mockMvc.perform(post("/api/v1/doctor/prescriptions")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prescRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.encounterId", is(encounterId.toString())))
                .andExpect(jsonPath("$.medicationName", is("Augmentin 875/125mg")))
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    @DisplayName("Doctor issuance should reject prescription if encounter belongs to a different patient")
    void testDoctorIssuanceFailsWhenEncounterBelongsToDifferentPatient() throws Exception {
        String patientToken1 = registerAndGetToken("patient.presc.a@apollo.local", "PATIENT");
        String patientToken2 = registerAndGetToken("patient.presc.b@apollo.local", "PATIENT");
        String doctorToken = registerAndGetToken("doctor.presc3@apollo.local", "DOCTOR");

        PatientProfile patientA = getPatientProfile(patientToken1);
        PatientProfile patientB = getPatientProfile(patientToken2);

        // Unlock patient B vault and create encounter for patient B
        MvcResult grantResult = mockMvc.perform(post("/api/v1/patient/vault/access-grants")
                        .header("Authorization", "Bearer " + patientToken2))
                .andExpect(status().isCreated())
                .andReturn();
        String accessCode = objectMapper.readTree(grantResult.getResponse().getContentAsString()).get("accessCode").asText();

        mockMvc.perform(post("/api/v1/doctor/vault/unlock")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UnlockVaultRequest.builder().accessCode(accessCode).build())))
                .andExpect(status().isOk());

        CreateEncounterRequest encRequest = CreateEncounterRequest.builder()
                .patientId(patientB.getId())
                .diagnosis("Pharyngitis")
                .clinicalNotes("Routine examination.")
                .encounterDate(LocalDate.now())
                .build();

        MvcResult encResult = mockMvc.perform(post("/api/v1/doctor/encounters")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(encRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID patientBEncounterId = UUID.fromString(objectMapper.readTree(encResult.getResponse().getContentAsString()).get("id").asText());

        // Doctor unlocks Patient A vault session as well
        unlockVaultSession(patientToken1, doctorToken);

        // Doctor tries to issue prescription for Patient A referencing Patient B's encounter
        CreatePrescriptionRequest badPrescRequest = CreatePrescriptionRequest.builder()
                .patientId(patientA.getId())
                .encounterId(patientBEncounterId)
                .medicationName("Penicillin VK 500mg")
                .dosage("1 tablet QID")
                .instructions("Take on empty stomach.")
                .expiresAt(Instant.now().plus(10, ChronoUnit.DAYS))
                .build();

        mockMvc.perform(post("/api/v1/doctor/prescriptions")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badPrescRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Clinical encounter does not belong to the specified patient.")));
    }

    @Test
    @DisplayName("Patient should retrieve all prescriptions and filter by status")
    void testPatientRetrievesAndFiltersPrescriptions() throws Exception {
        String patientToken = registerAndGetToken("patient.presc.query@apollo.local", "PATIENT");
        String doctorToken = registerAndGetToken("doctor.presc.query@apollo.local", "DOCTOR");

        PatientProfile patient = getPatientProfile(patientToken);

        unlockVaultSession(patientToken, doctorToken);

        // Doctor issues 2 prescriptions
        CreatePrescriptionRequest presc1 = CreatePrescriptionRequest.builder()
                .patientId(patient.getId())
                .medicationName("Amoxicillin 500mg")
                .dosage("1 TID")
                .instructions("With meals")
                .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .build();

        MvcResult result1 = mockMvc.perform(post("/api/v1/doctor/prescriptions")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(presc1)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID prescId1 = UUID.fromString(objectMapper.readTree(result1.getResponse().getContentAsString()).get("id").asText());

        CreatePrescriptionRequest presc2 = CreatePrescriptionRequest.builder()
                .patientId(patient.getId())
                .medicationName("Ibuprofen 400mg")
                .dosage("1 PRN")
                .instructions("For pain")
                .expiresAt(Instant.now().plus(60, ChronoUnit.DAYS))
                .build();

        mockMvc.perform(post("/api/v1/doctor/prescriptions")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(presc2)))
                .andExpect(status().isCreated());

        // 1. Patient queries all prescriptions (unfiltered)
        mockMvc.perform(get("/api/v1/patient/vault/prescriptions")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // 2. Patient marks presc1 as FULFILLED
        UpdatePrescriptionStatusRequest fulfillRequest = UpdatePrescriptionStatusRequest.builder()
                .status(PrescriptionStatus.FULFILLED)
                .build();

        mockMvc.perform(patch("/api/v1/prescriptions/" + prescId1 + "/status")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fulfillRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(prescId1.toString())))
                .andExpect(jsonPath("$.status", is("FULFILLED")));

        // 3. Patient queries with status=ACTIVE
        mockMvc.perform(get("/api/v1/patient/vault/prescriptions")
                        .header("Authorization", "Bearer " + patientToken)
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].medicationName", is("Ibuprofen 400mg")))
                .andExpect(jsonPath("$[0].status", is("ACTIVE")));

        // 4. Patient queries with status=FULFILLED
        mockMvc.perform(get("/api/v1/patient/vault/prescriptions")
                        .header("Authorization", "Bearer " + patientToken)
                        .param("status", "FULFILLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].medicationName", is("Amoxicillin 500mg")))
                .andExpect(jsonPath("$[0].status", is("FULFILLED")));
    }

    @Test
    @DisplayName("Doctor should successfully cancel an active prescription")
    void testDoctorCancelsPrescription() throws Exception {
        String patientToken = registerAndGetToken("patient.presc.cancel@apollo.local", "PATIENT");
        String doctorToken = registerAndGetToken("doctor.presc.cancel@apollo.local", "DOCTOR");

        PatientProfile patient = getPatientProfile(patientToken);

        unlockVaultSession(patientToken, doctorToken);

        CreatePrescriptionRequest request = CreatePrescriptionRequest.builder()
                .patientId(patient.getId())
                .medicationName("Lisinopril 10mg")
                .dosage("1 daily")
                .instructions("Morning")
                .expiresAt(Instant.now().plus(90, ChronoUnit.DAYS))
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/doctor/prescriptions")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID prescId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());

        // Doctor cancels prescription
        UpdatePrescriptionStatusRequest cancelReq = UpdatePrescriptionStatusRequest.builder()
                .status(PrescriptionStatus.CANCELLED)
                .build();

        mockMvc.perform(patch("/api/v1/prescriptions/" + prescId + "/status")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    @Test
    @DisplayName("Patient should be rejected when trying to cancel a prescription")
    void testPatientCannotCancelPrescription() throws Exception {
        String patientToken = registerAndGetToken("patient.presc.invalid1@apollo.local", "PATIENT");
        String doctorToken = registerAndGetToken("doctor.presc.invalid1@apollo.local", "DOCTOR");

        PatientProfile patient = getPatientProfile(patientToken);

        unlockVaultSession(patientToken, doctorToken);

        CreatePrescriptionRequest request = CreatePrescriptionRequest.builder()
                .patientId(patient.getId())
                .medicationName("Metformin 500mg")
                .dosage("1 daily")
                .instructions("With dinner")
                .expiresAt(Instant.now().plus(90, ChronoUnit.DAYS))
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/doctor/prescriptions")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID prescId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());

        UpdatePrescriptionStatusRequest cancelReq = UpdatePrescriptionStatusRequest.builder()
                .status(PrescriptionStatus.CANCELLED)
                .build();

        mockMvc.perform(patch("/api/v1/prescriptions/" + prescId + "/status")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Patients can only update prescription status to FULFILLED.")));
    }

    @Test
    @DisplayName("Doctor should be rejected when trying to mark prescription as fulfilled")
    void testDoctorCannotMarkAsFulfilled() throws Exception {
        String patientToken = registerAndGetToken("patient.presc.invalid2@apollo.local", "PATIENT");
        String doctorToken = registerAndGetToken("doctor.presc.invalid2@apollo.local", "DOCTOR");

        PatientProfile patient = getPatientProfile(patientToken);

        unlockVaultSession(patientToken, doctorToken);

        CreatePrescriptionRequest request = CreatePrescriptionRequest.builder()
                .patientId(patient.getId())
                .medicationName("Omeprazole 20mg")
                .dosage("1 daily before breakfast")
                .instructions("Take before meal")
                .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/doctor/prescriptions")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID prescId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());

        UpdatePrescriptionStatusRequest fulfillReq = UpdatePrescriptionStatusRequest.builder()
                .status(PrescriptionStatus.FULFILLED)
                .build();

        mockMvc.perform(patch("/api/v1/prescriptions/" + prescId + "/status")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fulfillReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Doctors cannot mark prescriptions as FULFILLED. Fulfillment is recorded by the patient upon pharmacy dispensing.")));
    }

    @Test
    @DisplayName("Terminal prescription cannot be updated again")
    void testTerminalPrescriptionCannotBeUpdated() throws Exception {
        String patientToken = registerAndGetToken("patient.presc.term@apollo.local", "PATIENT");
        String doctorToken = registerAndGetToken("doctor.presc.term@apollo.local", "DOCTOR");

        PatientProfile patient = getPatientProfile(patientToken);

        unlockVaultSession(patientToken, doctorToken);

        CreatePrescriptionRequest request = CreatePrescriptionRequest.builder()
                .patientId(patient.getId())
                .medicationName("Cetirizine 10mg")
                .dosage("1 nightly")
                .instructions("As needed")
                .expiresAt(Instant.now().plus(60, ChronoUnit.DAYS))
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/doctor/prescriptions")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID prescId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());

        // Cancel first
        mockMvc.perform(patch("/api/v1/prescriptions/" + prescId + "/status")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdatePrescriptionStatusRequest.builder().status(PrescriptionStatus.CANCELLED).build())))
                .andExpect(status().isOk());

        // Attempting to cancel again or update should fail
        mockMvc.perform(patch("/api/v1/prescriptions/" + prescId + "/status")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdatePrescriptionStatusRequest.builder().status(PrescriptionStatus.CANCELLED).build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Cannot update a prescription that has already been CANCELLED.")));
    }

    @Test
    @DisplayName("Cross-patient isolation: Patient B cannot update Patient A's prescription")
    void testCrossPatientIsolation() throws Exception {
        String patientTokenA = registerAndGetToken("patient.presc.iso.a@apollo.local", "PATIENT");
        String patientTokenB = registerAndGetToken("patient.presc.iso.b@apollo.local", "PATIENT");
        String doctorToken = registerAndGetToken("doctor.presc.iso@apollo.local", "DOCTOR");

        PatientProfile patientA = getPatientProfile(patientTokenA);

        unlockVaultSession(patientTokenA, doctorToken);

        CreatePrescriptionRequest request = CreatePrescriptionRequest.builder()
                .patientId(patientA.getId())
                .medicationName("Atorvastatin 20mg")
                .dosage("1 daily")
                .instructions("Bedtime")
                .expiresAt(Instant.now().plus(90, ChronoUnit.DAYS))
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/doctor/prescriptions")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID prescId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());

        // Patient B attempts to update Patient A's prescription
        mockMvc.perform(patch("/api/v1/prescriptions/" + prescId + "/status")
                        .header("Authorization", "Bearer " + patientTokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdatePrescriptionStatusRequest.builder().status(PrescriptionStatus.FULFILLED).build())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("You do not have permission to update this prescription.")));
    }

    @Test
    @DisplayName("Cross-doctor isolation: Doctor B cannot cancel Doctor A's prescription")
    void testCrossDoctorIsolation() throws Exception {
        String patientToken = registerAndGetToken("patient.presc.dociso@apollo.local", "PATIENT");
        String doctorTokenA = registerAndGetToken("doctor.presc.dociso.a@apollo.local", "DOCTOR");
        String doctorTokenB = registerAndGetToken("doctor.presc.dociso.b@apollo.local", "DOCTOR");

        PatientProfile patient = getPatientProfile(patientToken);

        unlockVaultSession(patientToken, doctorTokenA);

        CreatePrescriptionRequest request = CreatePrescriptionRequest.builder()
                .patientId(patient.getId())
                .medicationName("Levothyroxine 50mcg")
                .dosage("1 daily")
                .instructions("Morning on empty stomach")
                .expiresAt(Instant.now().plus(90, ChronoUnit.DAYS))
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/doctor/prescriptions")
                        .header("Authorization", "Bearer " + doctorTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID prescId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());

        // Doctor B attempts to cancel Doctor A's prescription
        mockMvc.perform(patch("/api/v1/prescriptions/" + prescId + "/status")
                        .header("Authorization", "Bearer " + doctorTokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdatePrescriptionStatusRequest.builder().status(PrescriptionStatus.CANCELLED).build())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("You do not have permission to update this prescription.")));
    }

    @Test
    @DisplayName("Patient should receive 403 Forbidden when attempting to issue prescriptions")
    void testPatientCannotIssuePrescription() throws Exception {
        String patientToken = registerAndGetToken("patient.presc.role@apollo.local", "PATIENT");

        CreatePrescriptionRequest request = CreatePrescriptionRequest.builder()
                .patientId(UUID.randomUUID())
                .medicationName("Illegal drug")
                .dosage("1 pill")
                .instructions("None")
                .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .build();

        mockMvc.perform(post("/api/v1/doctor/prescriptions")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated requests should receive 401 Unauthorized")
    void testUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/patient/vault/prescriptions"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/doctor/prescriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/prescriptions/" + UUID.randomUUID() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}

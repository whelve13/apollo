package com.apollo.controller;

import com.apollo.domain.entity.AccessGrant;
import com.apollo.domain.entity.HealthCondition;
import com.apollo.domain.entity.PatientProfile;
import com.apollo.domain.entity.User;
import com.apollo.domain.enums.HealthConditionType;
import com.apollo.domain.enums.SourceType;
import com.apollo.dto.auth.RegisterDoctorRequest;
import com.apollo.dto.auth.RegisterPatientRequest;
import com.apollo.dto.doctor.CreateEncounterRequest;
import com.apollo.dto.doctor.DoctorConditionInput;
import com.apollo.dto.doctor.UnlockVaultRequest;
import com.apollo.dto.vault.CreateHealthConditionRequest;
import com.apollo.repository.AccessGrantRepository;
import com.apollo.repository.ActiveVaultSessionRepository;
import com.apollo.repository.ClinicalEncounterRepository;
import com.apollo.repository.DoctorProfileRepository;
import com.apollo.repository.HealthConditionRepository;
import com.apollo.repository.LabTestResultRepository;
import com.apollo.repository.PatientProfileRepository;
import com.apollo.repository.PrescriptionRepository;
import com.apollo.repository.UserRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DoctorVaultIntegrationTests {

    private com.fasterxml.jackson.databind.JsonNode generatePin(String patientToken) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/patient/vault/access-grants")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private com.fasterxml.jackson.databind.JsonNode connect(String doctorToken, String pin) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/doctor/vault/unlock")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UnlockVaultRequest.builder().accessCode(pin).build())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void assertWritesDenied(String doctorToken, UUID patientId) throws Exception {
        var payloads = java.util.Map.of(
                "/api/v1/doctor/encounters", java.util.Map.of("patientId", patientId, "diagnosis", "Test",
                        "clinicalNotes", "Notes", "encounterDate", LocalDate.now()),
                "/api/v1/doctor/prescriptions", java.util.Map.of("patientId", patientId, "medicationName", "Test",
                        "dosage", "1 daily", "instructions", "With food", "expiresAt", Instant.now().plusSeconds(3600)),
                "/api/v1/doctor/test-results", java.util.Map.of("patientId", patientId, "testName", "Glucose",
                        "numericValue", 90, "unit", "mg/dL", "recordedAt", Instant.now().minusSeconds(5)),
                "/api/v1/doctor/vision-prescriptions", java.util.Map.of("patientId", patientId,
                        "rightEye", java.util.Map.of("sphere", -1), "leftEye", java.util.Map.of("sphere", -1)));
        for (var entry : payloads.entrySet()) {
            mockMvc.perform(post(entry.getKey()).header("Authorization", "Bearer " + doctorToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(entry.getValue())))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void connectionsSurviveNewRequestsAndRemainScopedToTheirDoctor() throws Exception {
        String firstPatient = registerAndGetToken("first@apollo.local", "PATIENT");
        String secondPatient = registerAndGetToken("second@apollo.local", "PATIENT");
        String doctor = registerAndGetToken("owner@apollo.local", "DOCTOR");
        String otherDoctor = registerAndGetToken("other@apollo.local", "DOCTOR");
        var firstPin = generatePin(firstPatient);
        var firstVault = connect(doctor, firstPin.get("accessCode").asText());
        var secondVault = connect(doctor, generatePin(secondPatient).get("accessCode").asText());
        assertThat(Instant.parse(firstVault.get("sessionExpiresAt").asText()))
                .isEqualTo(Instant.parse(firstPin.get("expiresAt").asText()));

        mockMvc.perform(get("/api/v1/doctor/vault/sessions").header("Authorization", "Bearer " + doctor))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].accessCode").isEmpty());
        for (var vault : List.of(firstVault, secondVault)) {
            String patientId = vault.get("patient").get("id").asText();
            mockMvc.perform(get("/api/v1/doctor/vault/patients/" + patientId)
                            .header("Authorization", "Bearer " + doctor))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.patient.id", is(patientId)));
            mockMvc.perform(get("/api/v1/doctor/vault/patients/" + patientId)
                            .header("Authorization", "Bearer " + otherDoctor))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/doctor/vault/sessions").header("Authorization", "Bearer " + otherDoctor))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/v1/doctor/vault/sessions").header("Authorization", "Bearer " + firstPatient))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/doctor/vault/sessions")).andExpect(status().isUnauthorized());
    }

    @Test
    void replacingPinRevokesConsumedPinConnectionAndOnlyThatPatient() throws Exception {
        String first = registerAndGetToken("replace@apollo.local", "PATIENT");
        String second = registerAndGetToken("unchanged@apollo.local", "PATIENT");
        String doctor = registerAndGetToken("replace.doctor@apollo.local", "DOCTOR");
        var firstVault = connect(doctor, generatePin(first).get("accessCode").asText());
        var secondVault = connect(doctor, generatePin(second).get("accessCode").asText());
        var newPin = generatePin(first);
        UUID patientId = UUID.fromString(firstVault.get("patient").get("id").asText());
        mockMvc.perform(get("/api/v1/doctor/vault/sessions").header("Authorization", "Bearer " + doctor))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].patient.id", is(secondVault.get("patient").get("id").asText())));
        mockMvc.perform(get("/api/v1/doctor/vault/patients/" + patientId)
                        .header("Authorization", "Bearer " + doctor)).andExpect(status().isForbidden());
        assertWritesDenied(doctor, patientId);
        connect(doctor, newPin.get("accessCode").asText());
        mockMvc.perform(get("/api/v1/doctor/vault/sessions").header("Authorization", "Bearer " + doctor))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void explicitRevocationBlocksReadsAndWritesEvenAfterPinWasConsumed() throws Exception {
        String patient = registerAndGetToken("revoke@apollo.local", "PATIENT");
        String doctor = registerAndGetToken("revoke.doctor@apollo.local", "DOCTOR");
        var vault = connect(doctor, generatePin(patient).get("accessCode").asText());
        mockMvc.perform(delete("/api/v1/patient/vault/access-grants").header("Authorization", "Bearer " + doctor))
                .andExpect(status().isForbidden());
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(delete("/api/v1/patient/vault/access-grants").header("Authorization", "Bearer " + patient))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(get("/api/v1/doctor/vault/sessions").header("Authorization", "Bearer " + doctor))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        assertWritesDenied(doctor, UUID.fromString(vault.get("patient").get("id").asText()));
    }

    @Test
    void expiredPinBlocksConnectionEvenIfSessionExpiryIsStillInFuture() throws Exception {
        String patient = registerAndGetToken("expiry@apollo.local", "PATIENT");
        String doctor = registerAndGetToken("expiry.doctor@apollo.local", "DOCTOR");
        var pin = generatePin(patient);
        var vault = connect(doctor, pin.get("accessCode").asText());
        AccessGrant grant = accessGrantRepository.findById(UUID.fromString(pin.get("id").asText())).orElseThrow();
        grant.setExpiresAt(Instant.now().minusSeconds(1));
        accessGrantRepository.save(grant);
        mockMvc.perform(get("/api/v1/doctor/vault/sessions").header("Authorization", "Bearer " + doctor))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        UUID patientId = UUID.fromString(vault.get("patient").get("id").asText());
        mockMvc.perform(get("/api/v1/doctor/vault/patients/" + patientId)
                        .header("Authorization", "Bearer " + doctor)).andExpect(status().isForbidden());
        assertWritesDenied(doctor, patientId);
    }

    @Test
    void disconnectingOnePatientDoesNotRemoveAnotherConnection() throws Exception {
        String first = registerAndGetToken("disconnect.first@apollo.local", "PATIENT");
        String second = registerAndGetToken("disconnect.second@apollo.local", "PATIENT");
        String doctor = registerAndGetToken("disconnect.doctor@apollo.local", "DOCTOR");
        String other = registerAndGetToken("disconnect.other@apollo.local", "DOCTOR");
        var firstVault = connect(doctor, generatePin(first).get("accessCode").asText());
        var secondVault = connect(doctor, generatePin(second).get("accessCode").asText());
        String patientId = firstVault.get("patient").get("id").asText();
        // Another doctor cannot disconnect the owner's connection.
        mockMvc.perform(delete("/api/v1/doctor/vault/patients/" + patientId)
                        .header("Authorization", "Bearer " + other)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/doctor/vault/sessions").header("Authorization", "Bearer " + doctor))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(delete("/api/v1/doctor/vault/patients/" + patientId)
                        .header("Authorization", "Bearer " + doctor)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/doctor/vault/sessions").header("Authorization", "Bearer " + doctor))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].patient.id", is(secondVault.get("patient").get("id").asText())));
    }

    @Test
    void legacySessionsWithoutAPinCannotBeRestoredOrAuthorizeWrites() throws Exception {
        registerAndGetToken("legacy.patient@apollo.local", "PATIENT");
        String doctorToken = registerAndGetToken("legacy.doctor@apollo.local", "DOCTOR");
        var patient = patientProfileRepository.findByUserId(userRepository.findByEmail("legacy.patient@apollo.local")
                .orElseThrow().getId()).orElseThrow();
        var doctor = doctorProfileRepository.findByUserId(userRepository.findByEmail("legacy.doctor@apollo.local")
                .orElseThrow().getId()).orElseThrow();
        activeVaultSessionRepository.save(com.apollo.domain.entity.ActiveVaultSession.builder()
                .doctor(doctor).patient(patient).expiresAt(Instant.now().plusSeconds(86_400)).build());
        mockMvc.perform(get("/api/v1/doctor/vault/sessions").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        assertWritesDenied(doctorToken, patient.getId());
    }

    @Test
    void collidingActivePinsAreRejectedWithoutConnectingTheWrongPatient() throws Exception {
        String first = registerAndGetToken("collision.first@apollo.local", "PATIENT");
        String second = registerAndGetToken("collision.second@apollo.local", "PATIENT");
        String doctor = registerAndGetToken("collision.doctor@apollo.local", "DOCTOR");
        var firstPin = generatePin(first);
        var secondPin = generatePin(second);
        AccessGrant grant = accessGrantRepository.findById(UUID.fromString(secondPin.get("id").asText())).orElseThrow();
        grant.setAccessCode(firstPin.get("accessCode").asText());
        accessGrantRepository.save(grant);
        mockMvc.perform(post("/api/v1/doctor/vault/unlock").header("Authorization", "Bearer " + doctor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UnlockVaultRequest.builder()
                                .accessCode(firstPin.get("accessCode").asText()).build())))
                .andExpect(status().isBadRequest());
        assertThat(activeVaultSessionRepository.count()).isZero();
    }

    @Test
    void concurrentUnlocksConsumeAPinOnlyOnce() throws Exception {
        String patient = registerAndGetToken("race.patient@apollo.local", "PATIENT");
        String firstDoctor = registerAndGetToken("race.first@apollo.local", "DOCTOR");
        String secondDoctor = registerAndGetToken("race.second@apollo.local", "DOCTOR");
        String body = objectMapper.writeValueAsString(UnlockVaultRequest.builder()
                .accessCode(generatePin(patient).get("accessCode").asText()).build());
        var start = new java.util.concurrent.CyclicBarrier(2);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            List<java.util.concurrent.Callable<Integer>> attempts = List.of(firstDoctor, secondDoctor).stream()
                    .<java.util.concurrent.Callable<Integer>>map(doctor -> () -> {
                        start.await(5, java.util.concurrent.TimeUnit.SECONDS);
                        return mockMvc.perform(post("/api/v1/doctor/vault/unlock")
                                        .header("Authorization", "Bearer " + doctor)
                                        .contentType(MediaType.APPLICATION_JSON).content(body))
                                .andReturn().getResponse().getStatus();
                    }).toList();
            var results = executor.invokeAll(attempts, 10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(List.of(results.get(0).get(), results.get(1).get()))
                    .containsExactlyInAnyOrder(200, 400);
        }
        assertThat(activeVaultSessionRepository.count()).isEqualTo(1);
    }

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

    @Test
    @DisplayName("Doctor should successfully unlock patient vault using valid 6-digit PIN and review history")
    void testSuccessfulVaultUnlockWithValidPin() throws Exception {
        // 1. Patient registers & adds baseline condition
        String patientToken = registerAndGetToken("patient.vault.test@apollo.local", "PATIENT");
        CreateHealthConditionRequest allergyReq = CreateHealthConditionRequest.builder()
                .title("Shellfish Allergy")
                .type(HealthConditionType.ALLERGY)
                .build();
        mockMvc.perform(post("/api/v1/patient/vault/conditions")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(allergyReq)))
                .andExpect(status().isCreated());

        // 2. Patient generates 6-digit access PIN
        MvcResult grantResult = mockMvc.perform(post("/api/v1/patient/vault/access-grants")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isCreated())
                .andReturn();

        String accessCode = objectMapper.readTree(grantResult.getResponse().getContentAsString()).get("accessCode").asText();

        // 3. Doctor registers and unlocks vault
        String doctorToken = registerAndGetToken("doctor.unlock@apollo.local", "DOCTOR");
        UnlockVaultRequest unlockRequest = UnlockVaultRequest.builder()
                .accessCode(accessCode)
                .build();

        mockMvc.perform(post("/api/v1/doctor/vault/unlock")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unlockRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patient.firstName", is("John")))
                .andExpect(jsonPath("$.patient.bloodType", is("O-")))
                .andExpect(jsonPath("$.allergies", hasSize(1)))
                .andExpect(jsonPath("$.allergies[0].title", is("Shellfish Allergy")))
                .andExpect(jsonPath("$.allConditions", hasSize(1)));

        // 4. Verify PIN in database is now marked used
        AccessGrant grantInDb = accessGrantRepository.findByAccessCodeAndIsUsedFalseAndExpiresAtAfter(accessCode, Instant.now()).orElse(null);
        assertThat(grantInDb).isNull();
    }

    @Test
    @DisplayName("Doctor vault unlock should reject expired 6-digit PIN with 400 Bad Request")
    void testVaultUnlockRejectsExpiredPin() throws Exception {
        String patientToken = registerAndGetToken("patient.expired@apollo.local", "PATIENT");
        String doctorToken = registerAndGetToken("doctor.expired@apollo.local", "DOCTOR");

        MvcResult grantResult = mockMvc.perform(post("/api/v1/patient/vault/access-grants")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isCreated())
                .andReturn();

        String grantId = objectMapper.readTree(grantResult.getResponse().getContentAsString()).get("id").asText();
        String accessCode = objectMapper.readTree(grantResult.getResponse().getContentAsString()).get("accessCode").asText();

        // Expire the token manually in DB
        AccessGrant grant = accessGrantRepository.findById(UUID.fromString(grantId)).orElseThrow();
        grant.setExpiresAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        accessGrantRepository.save(grant);

        UnlockVaultRequest request = UnlockVaultRequest.builder().accessCode(accessCode).build();

        mockMvc.perform(post("/api/v1/doctor/vault/unlock")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", is("Invalid or expired consultation access PIN.")));
    }

    @Test
    @DisplayName("Doctor vault unlock should reject already consumed PIN with 400 Bad Request (single-use)")
    void testVaultUnlockRejectsAlreadyUsedPin() throws Exception {
        String patientToken = registerAndGetToken("patient.used@apollo.local", "PATIENT");
        String doctorToken = registerAndGetToken("doctor.used@apollo.local", "DOCTOR");

        MvcResult grantResult = mockMvc.perform(post("/api/v1/patient/vault/access-grants")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isCreated())
                .andReturn();

        String accessCode = objectMapper.readTree(grantResult.getResponse().getContentAsString()).get("accessCode").asText();
        UnlockVaultRequest request = UnlockVaultRequest.builder().accessCode(accessCode).build();

        // First unlock succeeds
        mockMvc.perform(post("/api/v1/doctor/vault/unlock")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Second unlock with same PIN fails
        mockMvc.perform(post("/api/v1/doctor/vault/unlock")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", is("Invalid or expired consultation access PIN.")));
    }

    @Test
    @DisplayName("Doctor vault unlock should reject non-existent PIN with 400 Bad Request")
    void testVaultUnlockRejectsNonExistentPin() throws Exception {
        String doctorToken = registerAndGetToken("doctor.nonexistent@apollo.local", "DOCTOR");

        UnlockVaultRequest request = UnlockVaultRequest.builder().accessCode("123456").build();

        mockMvc.perform(post("/api/v1/doctor/vault/unlock")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", is("Invalid or expired consultation access PIN.")));
    }

    @Test
    @DisplayName("Doctor should append clinical encounter directly using patientId and record DOCTOR_VERIFIED conditions")
    void testCreateClinicalEncounterSuccess() throws Exception {
        String patientToken = registerAndGetToken("patient.enc@apollo.local", "PATIENT");
        String doctorToken = registerAndGetToken("doctor.enc@apollo.local", "DOCTOR");

        User patientUser = userRepository.findByEmail("patient.enc@apollo.local").orElseThrow();
        PatientProfile patient = patientProfileRepository.findByUserId(patientUser.getId()).orElseThrow();

        // Patient generates access PIN and doctor unlocks vault to establish PIN-backed session
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

        CreateEncounterRequest encounterRequest = CreateEncounterRequest.builder()
                .patientId(patient.getId())
                .chiefComplaint("Sore throat and fever for 3 days")
                .diagnosis("Acute Streptococcal Pharyngitis")
                .clinicalNotes("Throat erythematous with tonsillar exudate. Rapid strep positive. Prescribing amoxicillin.")
                .encounterDate(LocalDate.now())
                .conditionsToAdd(List.of(
                        DoctorConditionInput.builder()
                                .title("Bacterial Pharyngitis")
                                .type(HealthConditionType.OTHER)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/doctor/encounters")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(encounterRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.patientId", is(patient.getId().toString())))
                .andExpect(jsonPath("$.chiefComplaint", is("Sore throat and fever for 3 days")))
                .andExpect(jsonPath("$.diagnosis", is("Acute Streptococcal Pharyngitis")))
                .andExpect(jsonPath("$.doctorName", is("Dr. Gregory House")))
                .andExpect(jsonPath("$.doctorSpecialty", is("Diagnostics")))
                .andExpect(jsonPath("$.conditionsAdded", hasSize(1)))
                .andExpect(jsonPath("$.conditionsAdded[0].title", is("Bacterial Pharyngitis")))
                .andExpect(jsonPath("$.conditionsAdded[0].sourceType", is(SourceType.DOCTOR_VERIFIED.name())));

        // Verify encounter exists in repository
        assertThat(clinicalEncounterRepository.findByPatientIdOrderByEncounterDateDescCreatedAtDesc(patient.getId())).hasSize(1);

        // Verify DOCTOR_VERIFIED condition exists in repository
        List<HealthCondition> conditions = healthConditionRepository.findByPatientIdOrderByDateRecordedDescCreatedAtDesc(patient.getId());
        assertThat(conditions).hasSize(1);
        assertThat(conditions.get(0).getSourceType()).isEqualTo(SourceType.DOCTOR_VERIFIED);
    }

    @Test
    @DisplayName("Patient role must be forbidden with 403 when attempting to access doctor endpoints")
    void testPatientRoleForbiddenOnDoctorEndpoints() throws Exception {
        String patientToken = registerAndGetToken("patient.forbidden@apollo.local", "PATIENT");

        UnlockVaultRequest unlockRequest = UnlockVaultRequest.builder().accessCode("123456").build();

        // 1. Patient attempting to unlock vault
        mockMvc.perform(post("/api/v1/doctor/vault/unlock")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unlockRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));

        CreateEncounterRequest encRequest = CreateEncounterRequest.builder()
                .patientId(UUID.randomUUID())
                .diagnosis("Test")
                .clinicalNotes("Test notes")
                .encounterDate(LocalDate.now())
                .build();

        // 2. Patient attempting to create clinical encounter
        mockMvc.perform(post("/api/v1/doctor/encounters")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(encRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("Unauthenticated request to doctor endpoints must be rejected with 401 Unauthorized")
    void testUnauthenticatedRejectedOnDoctorEndpoints() throws Exception {
        UnlockVaultRequest unlockRequest = UnlockVaultRequest.builder().accessCode("123456").build();

        mockMvc.perform(post("/api/v1/doctor/vault/unlock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unlockRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));

        CreateEncounterRequest encRequest = CreateEncounterRequest.builder()
                .patientId(UUID.randomUUID())
                .diagnosis("Test")
                .clinicalNotes("Test notes")
                .encounterDate(LocalDate.now())
                .build();

        mockMvc.perform(post("/api/v1/doctor/encounters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(encRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }
}

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DoctorVaultIntegrationTests {

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

        // Patient generates access PIN and doctor unlocks vault to establish 24h session
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

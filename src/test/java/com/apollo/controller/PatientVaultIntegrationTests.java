package com.apollo.controller;

import com.apollo.domain.entity.ClinicalEncounter;
import com.apollo.domain.entity.DoctorProfile;
import com.apollo.domain.entity.HealthCondition;
import com.apollo.domain.entity.PatientProfile;
import com.apollo.domain.entity.User;
import com.apollo.domain.enums.HealthConditionType;
import com.apollo.domain.enums.Role;
import com.apollo.domain.enums.SourceType;
import com.apollo.dto.auth.RegisterDoctorRequest;
import com.apollo.dto.auth.RegisterPatientRequest;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PatientVaultIntegrationTests {

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
                    .firstName("Sarah")
                    .lastName("Connor")
                    .dateOfBirth(LocalDate.of(1985, 2, 28))
                    .bloodType("B+")
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
                    .password("Password123!")
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
    @DisplayName("Patient should add and list baseline health conditions with PATIENT_DECLARED source")
    void testAddAndListPatientConditions() throws Exception {
        String token = registerAndGetToken("patient.conditions@apollo.local", "PATIENT");

        CreateHealthConditionRequest request = CreateHealthConditionRequest.builder()
                .title("Peanut Allergy")
                .type(HealthConditionType.ALLERGY)
                .dateRecorded(LocalDate.of(2021, 5, 10))
                .build();

        // 1. Add Condition
        mockMvc.perform(post("/api/v1/patient/vault/conditions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.title", is("Peanut Allergy")))
                .andExpect(jsonPath("$.type", is(HealthConditionType.ALLERGY.name())))
                .andExpect(jsonPath("$.sourceType", is(SourceType.PATIENT_DECLARED.name())))
                .andExpect(jsonPath("$.dateRecorded", is("2021-05-10")));

        // 2. List Conditions
        mockMvc.perform(get("/api/v1/patient/vault/conditions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("Peanut Allergy")))
                .andExpect(jsonPath("$[0].sourceType", is(SourceType.PATIENT_DECLARED.name())));
    }

    @Test
    @DisplayName("Patient should successfully delete own PATIENT_DECLARED condition")
    void testDeletePatientDeclaredCondition() throws Exception {
        String token = registerAndGetToken("patient.delete@apollo.local", "PATIENT");

        CreateHealthConditionRequest request = CreateHealthConditionRequest.builder()
                .title("Asthma")
                .type(HealthConditionType.CHRONIC_CONDITION)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/patient/vault/conditions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String conditionId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        // Delete condition
        mockMvc.perform(delete("/api/v1/patient/vault/conditions/" + conditionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify it is gone
        mockMvc.perform(get("/api/v1/patient/vault/conditions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Patient should be rejected with 403 Forbidden when attempting to delete DOCTOR_VERIFIED condition")
    void testCannotDeleteDoctorVerifiedCondition() throws Exception {
        String token = registerAndGetToken("patient.immutable@apollo.local", "PATIENT");
        User user = userRepository.findByEmail("patient.immutable@apollo.local").orElseThrow();
        PatientProfile patient = patientProfileRepository.findByUserId(user.getId()).orElseThrow();

        // Simulate a condition verified by a doctor
        HealthCondition verifiedCondition = HealthCondition.builder()
                .patient(patient)
                .title("Hypertension (Confirmed)")
                .type(HealthConditionType.CHRONIC_CONDITION)
                .sourceType(SourceType.DOCTOR_VERIFIED)
                .dateRecorded(LocalDate.now())
                .build();
        verifiedCondition = healthConditionRepository.save(verifiedCondition);

        // Attempt deletion
        mockMvc.perform(delete("/api/v1/patient/vault/conditions/" + verifiedCondition.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.message", is("Patients cannot delete doctor-verified clinical conditions.")));

        // Verify still in DB
        assertThat(healthConditionRepository.findById(verifiedCondition.getId())).isPresent();
    }

    @Test
    @DisplayName("Patient cannot delete a condition belonging to another patient")
    void testCannotDeleteAnotherPatientsCondition() throws Exception {
        String tokenA = registerAndGetToken("patientA@apollo.local", "PATIENT");
        String tokenB = registerAndGetToken("patientB@apollo.local", "PATIENT");

        CreateHealthConditionRequest request = CreateHealthConditionRequest.builder()
                .title("Diabetes Type 2")
                .type(HealthConditionType.CHRONIC_CONDITION)
                .build();

        MvcResult resultA = mockMvc.perform(post("/api/v1/patient/vault/conditions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String conditionIdA = objectMapper.readTree(resultA.getResponse().getContentAsString()).get("id").asText();

        // Patient B attempts to delete Patient A's condition
        mockMvc.perform(delete("/api/v1/patient/vault/conditions/" + conditionIdA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    @DisplayName("Patient should generate 6-digit access code with 15-minute expiry and revoke previous grants")
    void testGenerateAccessGrant() throws Exception {
        String token = registerAndGetToken("patient.grant@apollo.local", "PATIENT");

        // 1. Generate First Grant
        MvcResult firstResult = mockMvc.perform(post("/api/v1/patient/vault/access-grants")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.accessCode", matchesPattern("^\\d{6}$")))
                .andExpect(jsonPath("$.expiresAt", notNullValue()))
                .andExpect(jsonPath("$.isUsed", is(false)))
                .andReturn();

        String firstGrantId = objectMapper.readTree(firstResult.getResponse().getContentAsString()).get("id").asText();

        // 2. Generate Second Grant (should invalidate first grant)
        MvcResult secondResult = mockMvc.perform(post("/api/v1/patient/vault/access-grants")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessCode", matchesPattern("^\\d{6}$")))
                .andExpect(jsonPath("$.isUsed", is(false)))
                .andReturn();

        String secondGrantId = objectMapper.readTree(secondResult.getResponse().getContentAsString()).get("id").asText();

        assertThat(firstGrantId).isNotEqualTo(secondGrantId);

        // Verify first grant is now marked isUsed = true
        var firstGrantInDb = accessGrantRepository.findById(UUID.fromString(firstGrantId)).orElseThrow();
        assertThat(firstGrantInDb.isUsed()).isTrue();

        var secondGrantInDb = accessGrantRepository.findById(UUID.fromString(secondGrantId)).orElseThrow();
        assertThat(secondGrantInDb.isUsed()).isFalse();
        assertThat(secondGrantInDb.getExpiresAt()).isAfter(Instant.now().plus(14, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("Patient should retrieve complete consolidated vault timeline")
    void testGetPatientVaultTimeline() throws Exception {
        String patientToken = registerAndGetToken("patient.timeline@apollo.local", "PATIENT");
        User patientUser = userRepository.findByEmail("patient.timeline@apollo.local").orElseThrow();
        PatientProfile patient = patientProfileRepository.findByUserId(patientUser.getId()).orElseThrow();

        // Add 1 condition
        CreateHealthConditionRequest condReq = CreateHealthConditionRequest.builder()
                .title("Migraines")
                .type(HealthConditionType.CHRONIC_CONDITION)
                .build();
        mockMvc.perform(post("/api/v1/patient/vault/conditions")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(condReq)))
                .andExpect(status().isCreated());

        // Create a doctor & clinical encounter in DB
        User doctorUser = userRepository.save(User.builder()
                .email("dr.timeline@hospital.org")
                .passwordHash("pwd")
                .role(Role.ROLE_DOCTOR)
                .build());
        DoctorProfile doctor = doctorProfileRepository.save(DoctorProfile.builder()
                .user(doctorUser)
                .firstName("Leonard")
                .lastName("McCoy")
                .licenseNumber("MD-MCC-11")
                .specialty("General Surgery")
                .build());

        clinicalEncounterRepository.save(ClinicalEncounter.builder()
                .patient(patient)
                .doctor(doctor)
                .encounterDate(LocalDate.of(2024, 6, 1))
                .diagnosis("Acute Appendicitis")
                .clinicalNotes("Routine appendectomy performed. Recovery normal.")
                .build());

        // Fetch Timeline
        mockMvc.perform(get("/api/v1/patient/vault/timeline")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patient.firstName", is("Sarah")))
                .andExpect(jsonPath("$.patient.bloodType", is("B+")))
                .andExpect(jsonPath("$.conditions", hasSize(1)))
                .andExpect(jsonPath("$.conditions[0].title", is("Migraines")))
                .andExpect(jsonPath("$.encounters", hasSize(1)))
                .andExpect(jsonPath("$.encounters[0].doctorName", is("Dr. Leonard McCoy")))
                .andExpect(jsonPath("$.encounters[0].diagnosis", is("Acute Appendicitis")));
    }

    @Test
    @DisplayName("Doctor role must be forbidden with 403 when attempting to access patient vault endpoints")
    void testDoctorRoleForbiddenOnPatientVault() throws Exception {
        String doctorToken = registerAndGetToken("doctor.forbidden@apollo.local", "DOCTOR");

        CreateHealthConditionRequest request = CreateHealthConditionRequest.builder()
                .title("Should Fail")
                .type(HealthConditionType.OTHER)
                .build();

        // 1. Doctor attempting to add condition to patient vault
        mockMvc.perform(post("/api/v1/patient/vault/conditions")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));

        // 2. Doctor attempting to generate patient access grant
        mockMvc.perform(post("/api/v1/patient/vault/access-grants")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));

        // 3. Doctor attempting to access patient timeline directly
        mockMvc.perform(get("/api/v1/patient/vault/timeline")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("Unauthenticated request to patient vault should be rejected with 401 Unauthorized")
    void testUnauthenticatedAccessRejected() throws Exception {
        mockMvc.perform(get("/api/v1/patient/vault/timeline"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }
}

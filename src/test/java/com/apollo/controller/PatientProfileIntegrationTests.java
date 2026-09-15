package com.apollo.controller;

import com.apollo.dto.auth.RegisterDoctorRequest;
import com.apollo.dto.auth.RegisterPatientRequest;
import com.apollo.dto.patient.UpdatePatientProfileRequest;
import com.apollo.dto.vault.CreateHealthConditionRequest;
import com.apollo.domain.enums.HealthConditionType;
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

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PatientProfileIntegrationTests {

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

    private String registerPatientAndGetToken(String email) throws Exception {
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
    }

    private String registerDoctorAndGetToken(String email) throws Exception {
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

    @Test
    @DisplayName("PATCH /api/v1/patient/profile successfully updates baseline attributes and returns 200")
    void updatePatientProfile_Success() throws Exception {
        String token = registerPatientAndGetToken("sarah.profile@test.com");

        UpdatePatientProfileRequest updateRequest = new UpdatePatientProfileRequest(
                "FEMALE",
                174.5,
                68.0
        );

        mockMvc.perform(patch("/api/v1/patient/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName", is("Sarah")))
                .andExpect(jsonPath("$.lastName", is("Connor")))
                .andExpect(jsonPath("$.bloodType", is("B+")))
                .andExpect(jsonPath("$.gender", is("FEMALE")))
                .andExpect(jsonPath("$.heightCm", is(174.5)))
                .andExpect(jsonPath("$.weightKg", is(68.0)));

        // Verify GET /api/v1/patient/profile returns updated attributes
        mockMvc.perform(get("/api/v1/patient/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gender", is("FEMALE")))
                .andExpect(jsonPath("$.heightCm", is(174.5)))
                .andExpect(jsonPath("$.weightKg", is(68.0)));

        // Verify GET /api/v1/auth/me returns updated attributes
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gender", is("FEMALE")))
                .andExpect(jsonPath("$.heightCm", is(174.5)))
                .andExpect(jsonPath("$.weightKg", is(68.0)));
    }

    @Test
    @DisplayName("PATCH /api/v1/patient/vault/profile alias endpoint updates baseline attributes")
    void updatePatientVaultProfileAlias_Success() throws Exception {
        String token = registerPatientAndGetToken("sarah.vault.alias@test.com");

        UpdatePatientProfileRequest updateRequest = new UpdatePatientProfileRequest(
                "OTHER",
                180.0,
                75.0
        );

        mockMvc.perform(patch("/api/v1/patient/vault/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gender", is("OTHER")))
                .andExpect(jsonPath("$.heightCm", is(180.0)))
                .andExpect(jsonPath("$.weightKg", is(75.0)));
    }

    @Test
    @DisplayName("PATCH /api/v1/patient/profile without token returns 401")
    void updatePatientProfile_Unauthorized() throws Exception {
        UpdatePatientProfileRequest updateRequest = new UpdatePatientProfileRequest("MALE", 180.0, 80.0);

        mockMvc.perform(patch("/api/v1/patient/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /api/v1/patient/profile with Doctor role returns 403 Forbidden")
    void updatePatientProfile_DoctorForbidden() throws Exception {
        String doctorToken = registerDoctorAndGetToken("dr.house.patient@test.com");
        UpdatePatientProfileRequest updateRequest = new UpdatePatientProfileRequest("MALE", 180.0, 80.0);

        mockMvc.perform(patch("/api/v1/patient/profile")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/patient/vault/conditions allows patient to record baseline conditions (allergies, chronic, lifestyle)")
    void recordBaselineConditions_Success() throws Exception {
        String token = registerPatientAndGetToken("sarah.conditions@test.com");

        // 1. Record Allergy
        CreateHealthConditionRequest allergy = CreateHealthConditionRequest.builder()
                .title("Penicillin Allergy")
                .type(HealthConditionType.ALLERGY)
                .dateRecorded(LocalDate.of(2020, 1, 10))
                .build();

        mockMvc.perform(post("/api/v1/patient/vault/conditions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(allergy)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Penicillin Allergy")))
                .andExpect(jsonPath("$.type", is("ALLERGY")))
                .andExpect(jsonPath("$.sourceType", is("PATIENT_DECLARED")));

        // 2. Record Chronic Condition
        CreateHealthConditionRequest chronic = CreateHealthConditionRequest.builder()
                .title("Hypertension")
                .type(HealthConditionType.CHRONIC_CONDITION)
                .dateRecorded(LocalDate.of(2022, 5, 20))
                .build();

        mockMvc.perform(post("/api/v1/patient/vault/conditions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chronic)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Hypertension")))
                .andExpect(jsonPath("$.type", is("CHRONIC_CONDITION")));

        // 3. Record Lifestyle (Smoking / Alcohol status)
        CreateHealthConditionRequest lifestyle = CreateHealthConditionRequest.builder()
                .title("Non-smoker, occasional alcohol consumption")
                .type(HealthConditionType.LIFESTYLE)
                .dateRecorded(LocalDate.of(2024, 1, 1))
                .build();

        mockMvc.perform(post("/api/v1/patient/vault/conditions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lifestyle)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Non-smoker, occasional alcohol consumption")))
                .andExpect(jsonPath("$.type", is("LIFESTYLE")));

        // Verify all 3 conditions appear in GET /api/v1/patient/vault/conditions
        mockMvc.perform(get("/api/v1/patient/vault/conditions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        // Verify timeline includes patient baseline attributes
        mockMvc.perform(get("/api/v1/patient/vault/timeline")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conditions", hasSize(3)));
    }
}

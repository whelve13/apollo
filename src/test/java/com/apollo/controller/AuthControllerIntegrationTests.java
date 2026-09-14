package com.apollo.controller;

import com.apollo.domain.enums.Role;
import com.apollo.dto.auth.LoginRequest;
import com.apollo.dto.auth.RegisterDoctorRequest;
import com.apollo.dto.auth.RegisterPatientRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTests {

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

    @Test
    @DisplayName("Should successfully register a patient and return JWT with profile metadata")
    void testRegisterPatientSuccess() throws Exception {
        RegisterPatientRequest request = RegisterPatientRequest.builder()
                .email("jane.doe@example.com")
                .password("Password123!")
                .firstName("Jane")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1992, 4, 18))
                .bloodType("A+")
                .build();

        mockMvc.perform(post("/api/v1/auth/register/patient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.role", is(Role.ROLE_PATIENT.name())))
                .andExpect(jsonPath("$.email", is("jane.doe@example.com")))
                .andExpect(jsonPath("$.fullName", is("Jane Doe")))
                .andExpect(jsonPath("$.userId", notNullValue()))
                .andExpect(jsonPath("$.profileId", notNullValue()));

        assertThat(userRepository.findByEmail("jane.doe@example.com")).isPresent();
        var user = userRepository.findByEmail("jane.doe@example.com").get();
        assertThat(user.getRole()).isEqualTo(Role.ROLE_PATIENT);
        assertThat(patientProfileRepository.findByUserId(user.getId())).isPresent();
    }

    @Test
    @DisplayName("Should successfully register a doctor and return JWT with doctor profile metadata")
    void testRegisterDoctorSuccess() throws Exception {
        RegisterDoctorRequest request = RegisterDoctorRequest.builder()
                .email("dr.strange@hospital.org")
                .password("DoctorPass123!")
                .firstName("Stephen")
                .lastName("Strange")
                .licenseNumber("MD-99887766")
                .specialty("Neurosurgery")
                .build();

        mockMvc.perform(post("/api/v1/auth/register/doctor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.role", is(Role.ROLE_DOCTOR.name())))
                .andExpect(jsonPath("$.email", is("dr.strange@hospital.org")))
                .andExpect(jsonPath("$.fullName", is("Dr. Stephen Strange")))
                .andExpect(jsonPath("$.userId", notNullValue()))
                .andExpect(jsonPath("$.profileId", notNullValue()));

        assertThat(userRepository.findByEmail("dr.strange@hospital.org")).isPresent();
        var user = userRepository.findByEmail("dr.strange@hospital.org").get();
        assertThat(user.getRole()).isEqualTo(Role.ROLE_DOCTOR);
        var doctor = doctorProfileRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(doctor.getLicenseNumber()).isEqualTo("MD-99887766");
    }

    @Test
    @DisplayName("Should reject registration when email already exists with 409 Conflict")
    void testRegisterDuplicateEmailFails() throws Exception {
        RegisterPatientRequest firstRequest = RegisterPatientRequest.builder()
                .email("duplicate@example.com")
                .password("Password123!")
                .firstName("First")
                .lastName("User")
                .dateOfBirth(LocalDate.of(1988, 1, 1))
                .build();

        mockMvc.perform(post("/api/v1/auth/register/patient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated());

        // Second registration with duplicate email
        RegisterPatientRequest duplicateRequest = RegisterPatientRequest.builder()
                .email("duplicate@example.com")
                .password("NewPassword123!")
                .firstName("Second")
                .lastName("User")
                .dateOfBirth(LocalDate.of(1995, 6, 12))
                .build();

        mockMvc.perform(post("/api/v1/auth/register/patient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("duplicate@example.com")));
    }

    @Test
    @DisplayName("Should reject doctor registration with duplicate license number with 409 Conflict")
    void testRegisterDoctorDuplicateLicenseFails() throws Exception {
        RegisterDoctorRequest doc1 = RegisterDoctorRequest.builder()
                .email("doc1@hospital.org")
                .password("Pass123456!")
                .firstName("Doctor")
                .lastName("One")
                .licenseNumber("LIC-SAME-123")
                .specialty("Pediatrics")
                .build();

        mockMvc.perform(post("/api/v1/auth/register/doctor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(doc1)))
                .andExpect(status().isCreated());

        RegisterDoctorRequest doc2 = RegisterDoctorRequest.builder()
                .email("doc2@hospital.org")
                .password("Pass123456!")
                .firstName("Doctor")
                .lastName("Two")
                .licenseNumber("LIC-SAME-123")
                .specialty("Cardiology")
                .build();

        mockMvc.perform(post("/api/v1/auth/register/doctor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(doc2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("LIC-SAME-123")));
    }

    @Test
    @DisplayName("Should authenticate registered user and return valid JWT on login")
    void testLoginSuccess() throws Exception {
        RegisterPatientRequest register = RegisterPatientRequest.builder()
                .email("login.test@example.com")
                .password("MySecretPass123!")
                .firstName("Alex")
                .lastName("Mercer")
                .dateOfBirth(LocalDate.of(1991, 7, 24))
                .build();

        mockMvc.perform(post("/api/v1/auth/register/patient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated());

        LoginRequest login = LoginRequest.builder()
                .email("login.test@example.com")
                .password("MySecretPass123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.email", is("login.test@example.com")))
                .andExpect(jsonPath("$.role", is(Role.ROLE_PATIENT.name())))
                .andExpect(jsonPath("$.fullName", is("Alex Mercer")));
    }

    @Test
    @DisplayName("Should return 401 Unauthorized for incorrect password")
    void testLoginInvalidPasswordFails() throws Exception {
        RegisterPatientRequest register = RegisterPatientRequest.builder()
                .email("wrong.pwd@example.com")
                .password("CorrectPass123!")
                .firstName("Bruce")
                .lastName("Wayne")
                .dateOfBirth(LocalDate.of(1980, 2, 19))
                .build();

        mockMvc.perform(post("/api/v1/auth/register/patient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated());

        LoginRequest login = LoginRequest.builder()
                .email("wrong.pwd@example.com")
                .password("IncorrectPassword!")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", is("Invalid email or password")));
    }

    @Test
    @DisplayName("Should return 401 Unauthorized for unknown email")
    void testLoginUnknownEmailFails() throws Exception {
        LoginRequest login = LoginRequest.builder()
                .email("nonexistent@example.com")
                .password("SomePassword123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", is("Invalid email or password")));
    }

    @Test
    @DisplayName("Should return current user details when accessing /me with valid JWT token")
    void testGetCurrentUserWithValidToken() throws Exception {
        RegisterPatientRequest register = RegisterPatientRequest.builder()
                .email("me.test@example.com")
                .password("SecretPass123!")
                .firstName("Clark")
                .lastName("Kent")
                .dateOfBirth(LocalDate.of(1985, 3, 20))
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register/patient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseBody).get("token").asText();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("me.test@example.com")))
                .andExpect(jsonPath("$.role", is(Role.ROLE_PATIENT.name())))
                .andExpect(jsonPath("$.fullName", is("Clark Kent")))
                .andExpect(jsonPath("$.userId", notNullValue()))
                .andExpect(jsonPath("$.profileId", notNullValue()));
    }

    @Test
    @DisplayName("Should reject /me endpoint with 401 Unauthorized when token is missing")
    void testGetCurrentUserWithoutTokenFails() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("Should reject registration when validation constraints fail (HTTP 400)")
    void testRegisterValidationFails() throws Exception {
        RegisterPatientRequest invalidRequest = RegisterPatientRequest.builder()
                .email("invalid-email-format")
                .password("short") // less than 8 chars
                .firstName("")
                .lastName("")
                .dateOfBirth(null)
                .build();

        mockMvc.perform(post("/api/v1/auth/register/patient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.fieldErrors", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.email", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.password", notNullValue()));
    }

    @Test
    @DisplayName("Should allow preflight and requests from http://localhost:* via permissive CORS")
    void testCorsConfiguration() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/v1/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type, Authorization"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Access-Control-Allow-Credentials", "true"));
    }
}

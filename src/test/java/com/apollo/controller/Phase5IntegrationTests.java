package com.apollo.controller;

import com.apollo.domain.entity.ActiveVaultSession;
import com.apollo.domain.entity.DoctorProfile;
import com.apollo.domain.entity.PatientProfile;
import com.apollo.domain.enums.DoctorRole;
import com.apollo.domain.enums.HealthConditionType;
import com.apollo.domain.enums.PrescriptionStatus;
import com.apollo.dto.admin.UpdateDoctorRoleRequest;
import com.apollo.dto.auth.RegisterDoctorRequest;
import com.apollo.dto.auth.RegisterPatientRequest;
import com.apollo.dto.doctor.CreateEncounterRequest;
import com.apollo.dto.doctor.UnlockVaultRequest;
import com.apollo.dto.lab.CreateLabTestResultRequest;
import com.apollo.dto.prescription.CreatePrescriptionRequest;
import com.apollo.dto.prescription.UpdatePrescriptionStatusRequest;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase5IntegrationTests {

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
                .bloodType("A+")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register/patient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String registerDoctorAndGetToken(String email, DoctorRole doctorRole) throws Exception {
        RegisterDoctorRequest request = RegisterDoctorRequest.builder()
                .email(email)
                .password("DoctorPass123!")
                .firstName("Stephen")
                .lastName("Strange")
                .licenseNumber("MD-" + UUID.randomUUID().toString().substring(0, 8))
                .specialty("Neurosurgery")
                .doctorRole(doctorRole)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register/doctor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
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
    @DisplayName("24-Hour Session Enforcement: Doctor cannot append encounter without active session, succeeds after unlock, fails if expired")
    void testActive24HourSessionLifecycle() throws Exception {
        String patientToken = registerPatientAndGetToken("patient.session@apollo.local");
        String doctorToken = registerDoctorAndGetToken("doctor.gp.session@apollo.local", DoctorRole.GENERAL_PRACTITIONER);

        PatientProfile patient = getPatientProfile(patientToken);
        DoctorProfile doctor = getDoctorProfile(doctorToken);

        CreateEncounterRequest encRequest = CreateEncounterRequest.builder()
                .patientId(patient.getId())
                .chiefComplaint("Headache")
                .diagnosis("Tension headache")
                .clinicalNotes("Prescribed rest.")
                .encounterDate(LocalDate.now())
                .build();

        // 1. Without unlocking, encounter creation fails with 403
        mockMvc.perform(post("/api/v1/doctor/encounters")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(encRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Doctor does not have an active 24-hour consultation session for this patient. Please unlock the vault with a valid patient access PIN.")));

        // 2. Unlock vault via 6-digit PIN establishes active 24h session
        unlockVaultSession(patientToken, doctorToken);

        boolean hasActiveSession = activeVaultSessionRepository.existsByDoctorIdAndPatientIdAndExpiresAtAfter(
                doctor.getId(), patient.getId(), Instant.now()
        );
        assertThat(hasActiveSession).isTrue();

        // 3. Downstream encounter creation succeeds
        mockMvc.perform(post("/api/v1/doctor/encounters")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(encRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()));

        // 4. Manually expire the session in DB
        ActiveVaultSession session = activeVaultSessionRepository
                .findTopByDoctorIdAndPatientIdAndExpiresAtAfterOrderByExpiresAtDesc(doctor.getId(), patient.getId(), Instant.now())
                .orElseThrow();
        session.setExpiresAt(Instant.now().minus(2, ChronoUnit.HOURS));
        activeVaultSessionRepository.save(session);

        // 5. Subsequent append is now blocked with 403
        mockMvc.perform(post("/api/v1/doctor/encounters")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(encRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Doctor does not have an active 24-hour consultation session for this patient. Please unlock the vault with a valid patient access PIN.")));
    }

    @Test
    @DisplayName("Lab Technician Boundaries: Can record lab test results; blocked from encounters and prescriptions; view is redacted")
    void testLabTechnicianBoundaries() throws Exception {
        String patientToken = registerPatientAndGetToken("patient.labtech@apollo.local");
        String labTechToken = registerDoctorAndGetToken("tech.lab@apollo.local", DoctorRole.LAB_TECHNICIAN);

        PatientProfile patient = getPatientProfile(patientToken);

        // Patient adds allergy
        mockMvc.perform(post("/api/v1/patient/vault/conditions")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateHealthConditionRequest.builder()
                                .title("Peanut Allergy")
                                .type(HealthConditionType.ALLERGY)
                                .build())))
                .andExpect(status().isCreated());

        // Generate access PIN
        MvcResult grantResult = mockMvc.perform(post("/api/v1/patient/vault/access-grants")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isCreated())
                .andReturn();
        String accessCode = objectMapper.readTree(grantResult.getResponse().getContentAsString()).get("accessCode").asText();

        // 1. Lab tech unlocks vault: view redacts allergies and clinical encounter history
        mockMvc.perform(post("/api/v1/doctor/vault/unlock")
                        .header("Authorization", "Bearer " + labTechToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UnlockVaultRequest.builder().accessCode(accessCode).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patient.firstName", is("Sarah")))
                .andExpect(jsonPath("$.allergies", empty()))
                .andExpect(jsonPath("$.chronicConditions", empty()))
                .andExpect(jsonPath("$.encounterHistory", empty()))
                .andExpect(jsonPath("$.activeSessionId", notNullValue()));

        // 2. Lab tech can record lab test results
        CreateLabTestResultRequest testReq = CreateLabTestResultRequest.builder()
                .patientId(patient.getId())
                .testName("Hemoglobin A1c")
                .numericValue(new BigDecimal("5.7"))
                .unit("%")
                .recordedAt(Instant.now())
                .build();

        mockMvc.perform(post("/api/v1/doctor/test-results")
                        .header("Authorization", "Bearer " + labTechToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.testName", is("Hemoglobin A1c")))
                .andExpect(jsonPath("$.numericValue", is(5.7)))
                .andExpect(jsonPath("$.unit", is("%")));

        // 3. Lab tech blocked from appending encounters
        CreateEncounterRequest encReq = CreateEncounterRequest.builder()
                .patientId(patient.getId())
                .diagnosis("Test")
                .clinicalNotes("Test notes")
                .encounterDate(LocalDate.now())
                .build();

        mockMvc.perform(post("/api/v1/doctor/encounters")
                        .header("Authorization", "Bearer " + labTechToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(encReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Only general practitioners and specialists are authorized to append clinical encounters.")));

        // 4. Lab tech blocked from issuing prescriptions
        CreatePrescriptionRequest prescReq = CreatePrescriptionRequest.builder()
                .patientId(patient.getId())
                .medicationName("Amoxicillin")
                .dosage("500mg")
                .instructions("1 TID")
                .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .build();

        mockMvc.perform(post("/api/v1/doctor/prescriptions")
                        .header("Authorization", "Bearer " + labTechToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prescReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Only general practitioners and specialists are authorized to issue prescriptions.")));
    }

    @Test
    @DisplayName("Pharmacist Boundaries: Can mark prescriptions FULFILLED; blocked from encounters, test results, and issuing prescriptions")
    void testPharmacistBoundaries() throws Exception {
        String patientToken = registerPatientAndGetToken("patient.pharm@apollo.local");
        String gpToken = registerDoctorAndGetToken("doctor.gp.pharm@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        String pharmToken = registerDoctorAndGetToken("pharmacist@apollo.local", DoctorRole.PHARMACIST);

        PatientProfile patient = getPatientProfile(patientToken);

        // GP unlocks and issues active prescription
        unlockVaultSession(patientToken, gpToken);

        CreatePrescriptionRequest prescReq = CreatePrescriptionRequest.builder()
                .patientId(patient.getId())
                .medicationName("Lipitor 20mg")
                .dosage("1 daily")
                .instructions("Nightly")
                .expiresAt(Instant.now().plus(60, ChronoUnit.DAYS))
                .build();

        MvcResult prescResult = mockMvc.perform(post("/api/v1/doctor/prescriptions")
                        .header("Authorization", "Bearer " + gpToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prescReq)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID prescId = UUID.fromString(objectMapper.readTree(prescResult.getResponse().getContentAsString()).get("id").asText());

        // Pharmacist marks the prescription as FULFILLED
        UpdatePrescriptionStatusRequest fulfillReq = UpdatePrescriptionStatusRequest.builder()
                .status(PrescriptionStatus.FULFILLED)
                .build();

        mockMvc.perform(patch("/api/v1/prescriptions/" + prescId + "/status")
                        .header("Authorization", "Bearer " + pharmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fulfillReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(prescId.toString())))
                .andExpect(jsonPath("$.status", is("FULFILLED")));

        // Pharmacist cannot record test results
        unlockVaultSession(patientToken, pharmToken);

        CreateLabTestResultRequest testReq = CreateLabTestResultRequest.builder()
                .patientId(patient.getId())
                .testName("Blood Pressure")
                .numericValue(new BigDecimal("120.0"))
                .unit("mmHg")
                .recordedAt(Instant.now())
                .build();

        mockMvc.perform(post("/api/v1/doctor/test-results")
                        .header("Authorization", "Bearer " + pharmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Pharmacists are not authorized to record lab test results.")));

        // Pharmacist cannot create encounters
        CreateEncounterRequest encReq = CreateEncounterRequest.builder()
                .patientId(patient.getId())
                .diagnosis("Test")
                .clinicalNotes("Test notes")
                .encounterDate(LocalDate.now())
                .build();

        mockMvc.perform(post("/api/v1/doctor/encounters")
                        .header("Authorization", "Bearer " + pharmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(encReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Only general practitioners and specialists are authorized to append clinical encounters.")));
    }

    @Test
    @DisplayName("Lab Test Results Time-Series Retrieval: Returns records sorted chronologically ascending for charting")
    void testLabTestResultsTimeSeries() throws Exception {
        String patientToken = registerPatientAndGetToken("patient.timeseries@apollo.local");
        String techToken = registerDoctorAndGetToken("tech.timeseries@apollo.local", DoctorRole.LAB_TECHNICIAN);

        PatientProfile patient = getPatientProfile(patientToken);
        unlockVaultSession(patientToken, techToken);

        Instant t1 = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant t2 = Instant.now().minus(5, ChronoUnit.DAYS);
        Instant t3 = Instant.now().minus(1, ChronoUnit.DAYS);

        // Record 3 glucose readings in non-chronological order
        CreateLabTestResultRequest r2 = CreateLabTestResultRequest.builder()
                .patientId(patient.getId())
                .testName("Fasting Blood Glucose")
                .numericValue(new BigDecimal("105.0"))
                .unit("mg/dL")
                .recordedAt(t2)
                .build();

        CreateLabTestResultRequest r1 = CreateLabTestResultRequest.builder()
                .patientId(patient.getId())
                .testName("Fasting Blood Glucose")
                .numericValue(new BigDecimal("92.0"))
                .unit("mg/dL")
                .recordedAt(t1)
                .build();

        CreateLabTestResultRequest r3 = CreateLabTestResultRequest.builder()
                .patientId(patient.getId())
                .testName("Fasting Blood Glucose")
                .numericValue(new BigDecimal("112.5"))
                .unit("mg/dL")
                .recordedAt(t3)
                .build();

        CreateLabTestResultRequest chol = CreateLabTestResultRequest.builder()
                .patientId(patient.getId())
                .testName("Total Cholesterol")
                .numericValue(new BigDecimal("185.0"))
                .unit("mg/dL")
                .recordedAt(t2)
                .build();

        mockMvc.perform(post("/api/v1/doctor/test-results")
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(r2)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/doctor/test-results")
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(r1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/doctor/test-results")
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(r3)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/doctor/test-results")
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chol)))
                .andExpect(status().isCreated());

        // 1. Patient retrieves all results: ordered ascending by recordedAt
        mockMvc.perform(get("/api/v1/patient/vault/test-results")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].numericValue", is(92.0))) // t1
                .andExpect(jsonPath("$[3].numericValue", is(112.5))); // t3

        // 2. Patient filters by testName
        mockMvc.perform(get("/api/v1/patient/vault/test-results")
                        .header("Authorization", "Bearer " + patientToken)
                        .param("testName", "Fasting Blood Glucose"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].numericValue", is(92.0)))
                .andExpect(jsonPath("$[1].numericValue", is(105.0)))
                .andExpect(jsonPath("$[2].numericValue", is(112.5)));
    }

    @Test
    @DisplayName("Admin Role Switch for Demo: Changes doctor role and immediately adjusts permission boundaries")
    void testAdminRoleSwitch() throws Exception {
        String patientToken = registerPatientAndGetToken("patient.adminswitch@apollo.local");
        String doctorToken = registerDoctorAndGetToken("doctor.switch@apollo.local", DoctorRole.LAB_TECHNICIAN);

        PatientProfile patient = getPatientProfile(patientToken);
        DoctorProfile doctor = getDoctorProfile(doctorToken);

        unlockVaultSession(patientToken, doctorToken);

        CreateEncounterRequest encReq = CreateEncounterRequest.builder()
                .patientId(patient.getId())
                .diagnosis("Hypertension")
                .clinicalNotes("Routine checkup")
                .encounterDate(LocalDate.now())
                .build();

        // 1. As LAB_TECHNICIAN, cannot create encounter
        mockMvc.perform(post("/api/v1/doctor/encounters")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(encReq)))
                .andExpect(status().isForbidden());

        // 2. Admin role switch to GENERAL_PRACTITIONER
        UpdateDoctorRoleRequest roleReq = UpdateDoctorRoleRequest.builder()
                .doctorRole(DoctorRole.GENERAL_PRACTITIONER)
                .build();

        mockMvc.perform(patch("/api/v1/admin/doctors/" + doctor.getId() + "/role")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctorId", is(doctor.getId().toString())))
                .andExpect(jsonPath("$.doctorRole", is("GENERAL_PRACTITIONER")));

        // 3. Immediately retry encounter creation: now succeeds
        mockMvc.perform(post("/api/v1/doctor/encounters")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(encReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.diagnosis", is("Hypertension")));
    }
}

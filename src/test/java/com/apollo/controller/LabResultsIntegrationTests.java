package com.apollo.controller;

import com.apollo.domain.entity.DoctorProfile;
import com.apollo.domain.entity.PatientProfile;
import com.apollo.domain.enums.DoctorRole;
import com.apollo.domain.enums.LabResultSource;
import com.apollo.domain.enums.LabResultStatus;
import com.apollo.dto.auth.RegisterDoctorRequest;
import com.apollo.dto.auth.RegisterPatientRequest;
import com.apollo.dto.doctor.UnlockVaultRequest;
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
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LabResultsIntegrationTests {

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

    // ---------------------------------------------------------------- helpers

    private String registerPatient(String email) throws Exception {
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

    private String registerDoctor(String email, DoctorRole role) throws Exception {
        RegisterDoctorRequest request = RegisterDoctorRequest.builder()
                .email(email)
                .password("DoctorPass123!")
                .firstName("Gregory")
                .lastName("House")
                .licenseNumber("MD-" + UUID.randomUUID().toString().substring(0, 8))
                .specialty("Diagnostics")
                .doctorRole(role)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register/doctor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private UUID profileId(String token) throws Exception {
        MvcResult me = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(me.getResponse().getContentAsString()).get("profileId").asText());
    }

    private PatientProfile patientProfile(String token) throws Exception {
        return patientProfileRepository.findById(profileId(token)).orElseThrow();
    }

    private DoctorProfile doctorProfile(String token) throws Exception {
        return doctorProfileRepository.findById(profileId(token)).orElseThrow();
    }

    private void unlock(String patientToken, String doctorToken) throws Exception {
        MvcResult grant = mockMvc.perform(post("/api/v1/patient/vault/access-grants")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isCreated())
                .andReturn();
        String code = objectMapper.readTree(grant.getResponse().getContentAsString()).get("accessCode").asText();

        mockMvc.perform(post("/api/v1/doctor/vault/unlock")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UnlockVaultRequest.builder().accessCode(code).build())))
                .andExpect(status().isOk());
    }

    private JsonNode unlockResponse(String patientToken, String doctorToken) throws Exception {
        MvcResult grant = mockMvc.perform(post("/api/v1/patient/vault/access-grants")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isCreated())
                .andReturn();
        String code = objectMapper.readTree(grant.getResponse().getContentAsString()).get("accessCode").asText();

        MvcResult unlocked = mockMvc.perform(post("/api/v1/doctor/vault/unlock")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UnlockVaultRequest.builder().accessCode(code).build())))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(unlocked.getResponse().getContentAsString());
    }

    private ResultActions postJson(String url, String token, String json) throws Exception {
        return mockMvc.perform(post(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions getPatientResults(String patientToken) throws Exception {
        return mockMvc.perform(get("/api/v1/patient/vault/test-results")
                .header("Authorization", "Bearer " + patientToken));
    }

    private static String hourAgo() {
        return Instant.now().minus(1, ChronoUnit.HOURS).toString();
    }

    // ---------------------------------------------------------------- section 1: fields

    @Test
    @DisplayName("Text result: valueText without numericValue or unit is stored, unit becomes empty string")
    void testTextOnlyResult() throws Exception {
        String patientToken = registerPatient("patient.text@apollo.local");
        String doctorToken = registerDoctor("doctor.text@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        PatientProfile patient = patientProfile(patientToken);
        unlock(patientToken, doctorToken);

        postJson("/api/v1/doctor/test-results", doctorToken, """
                {"patientId":"%s","testName":"Protein","valueText":" ++ ","recordedAt":"%s"}
                """.formatted(patient.getId(), hourAgo()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valueText", is("++")))
                .andExpect(jsonPath("$.numericValue", nullValue()))
                .andExpect(jsonPath("$.unit", is("")))
                .andExpect(jsonPath("$.status", is("FINAL")))
                .andExpect(jsonPath("$.source", is("DOCTOR_VERIFIED")));
    }

    @Test
    @DisplayName("Censored result: qualifier '<' round-trips on the wire together with the bound, reference range, flag and comment")
    void testCensoredResultWithReferenceFlagAndComment() throws Exception {
        String patientToken = registerPatient("patient.cens@apollo.local");
        String doctorToken = registerDoctor("doctor.cens@apollo.local", DoctorRole.LAB_TECHNICIAN);
        PatientProfile patient = patientProfile(patientToken);
        unlock(patientToken, doctorToken);

        postJson("/api/v1/doctor/test-results", doctorToken, """
                {"patientId":"%s","testName":"CRP","numericValue":0.5,"qualifier":"<","unit":"mg/L",
                 "referenceHigh":0.5,"flag":"NORMAL","comment":"Below detection limit","recordedAt":"%s"}
                """.formatted(patient.getId(), hourAgo()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numericValue", is(0.5)))
                .andExpect(jsonPath("$.qualifier", is("<")))
                .andExpect(jsonPath("$.referenceLow", nullValue()))
                .andExpect(jsonPath("$.referenceHigh", is(0.5)))
                .andExpect(jsonPath("$.flag", is("NORMAL")))
                .andExpect(jsonPath("$.comment", is("Below detection limit")));

        getPatientResults(patientToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].qualifier", is("<")))
                .andExpect(jsonPath("$[0].flag", is("NORMAL")));
    }

    @Test
    @DisplayName("Validation: a result needs a numericValue, valueText or comment; a qualifier needs a numericValue")
    void testValueValidation() throws Exception {
        String patientToken = registerPatient("patient.valid@apollo.local");
        String doctorToken = registerDoctor("doctor.valid@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        PatientProfile patient = patientProfile(patientToken);
        unlock(patientToken, doctorToken);

        // nothing to record
        postJson("/api/v1/doctor/test-results", doctorToken, """
                {"patientId":"%s","testName":"Protein","unit":"g/L","recordedAt":"%s"}
                """.formatted(patient.getId(), hourAgo()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.numericValue", notNullValue()));

        // blank text does not count as a value
        postJson("/api/v1/doctor/test-results", doctorToken, """
                {"patientId":"%s","testName":"Protein","valueText":"   ","recordedAt":"%s"}
                """.formatted(patient.getId(), hourAgo()))
                .andExpect(status().isBadRequest());

        // qualifier without a bound
        postJson("/api/v1/doctor/test-results", doctorToken, """
                {"patientId":"%s","testName":"CRP","valueText":"low","qualifier":">","recordedAt":"%s"}
                """.formatted(patient.getId(), hourAgo()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.qualifier", notNullValue()));

        // unknown qualifier / flag symbols are rejected
        postJson("/api/v1/doctor/test-results", doctorToken, """
                {"patientId":"%s","testName":"CRP","numericValue":5,"qualifier":"~","recordedAt":"%s"}
                """.formatted(patient.getId(), hourAgo()))
                .andExpect(status().isBadRequest());

        assertThat(labTestResultRepository.count()).isZero();
    }

    @Test
    @DisplayName("Legacy payload (numericValue + unit only) keeps working and gets FINAL / DOCTOR_VERIFIED defaults")
    void testLegacyPayloadStillWorks() throws Exception {
        String patientToken = registerPatient("patient.legacy@apollo.local");
        String doctorToken = registerDoctor("doctor.legacy@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        PatientProfile patient = patientProfile(patientToken);
        unlock(patientToken, doctorToken);

        postJson("/api/v1/doctor/test-results", doctorToken, """
                {"patientId":"%s","testName":"Hemoglobin A1c","numericValue":5.7,"unit":"%%","recordedAt":"%s"}
                """.formatted(patient.getId(), hourAgo()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numericValue", is(5.7)))
                .andExpect(jsonPath("$.unit", is("%")))
                .andExpect(jsonPath("$.qualifier", nullValue()))
                .andExpect(jsonPath("$.panelId", nullValue()))
                .andExpect(jsonPath("$.status", is("FINAL")))
                .andExpect(jsonPath("$.source", is("DOCTOR_VERIFIED")));
    }

    @Test
    @DisplayName("Drafts are visible only to the authoring doctor and never to the patient")
    void testDraftVisibility() throws Exception {
        String patientToken = registerPatient("patient.draft@apollo.local");
        String authorToken = registerDoctor("doctor.author@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        String otherToken = registerDoctor("doctor.other@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        PatientProfile patient = patientProfile(patientToken);
        unlock(patientToken, authorToken);

        postJson("/api/v1/doctor/test-results", authorToken, """
                {"patientId":"%s","testName":"TSH","numericValue":2.1,"unit":"mIU/L","status":"DRAFT","recordedAt":"%s"}
                """.formatted(patient.getId(), hourAgo()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("DRAFT")));

        // patient: nothing, also when filtering by name
        getPatientResults(patientToken).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/v1/patient/vault/test-results")
                        .header("Authorization", "Bearer " + patientToken)
                        .param("testName", "TSH"))
                .andExpect(jsonPath("$", hasSize(0)));

        // other doctor unlocking the vault: nothing
        JsonNode otherView = unlockResponse(patientToken, otherToken);
        assertThat(otherView.get("testResults")).isEmpty();

        // author re-unlocking: sees the draft
        JsonNode authorView = unlockResponse(patientToken, authorToken);
        assertThat(authorView.get("testResults")).hasSize(1);
        assertThat(authorView.get("testResults").get(0).get("status").asText()).isEqualTo("DRAFT");
    }

    // ---------------------------------------------------------------- section 2: panels

    @Test
    @DisplayName("Panel: mixed numeric, censored, text and comment entries are saved together under one panelId")
    void testPanelHappyPath() throws Exception {
        String patientToken = registerPatient("patient.panel@apollo.local");
        String doctorToken = registerDoctor("doctor.panel@apollo.local", DoctorRole.LAB_TECHNICIAN);
        PatientProfile patient = patientProfile(patientToken);
        unlock(patientToken, doctorToken);

        MvcResult created = postJson("/api/v1/doctor/test-results/panels", doctorToken, """
                {
                  "patientId": "%s",
                  "recordedAt": "%s",
                  "status": "FINAL",
                  "templateCode": "cbc",
                  "templateVersion": 1,
                  "entries": [
                    {"key":"hgb","testName":"Hemoglobin","numericValue":112,"unit":"g/L","referenceLow":120,"referenceHigh":155,"flag":"LOW"},
                    {"key":"crp","testName":"CRP","numericValue":5,"qualifier":"<","unit":"mg/L"},
                    {"key":"protein","testName":"Protein","valueText":"++"},
                    {"key":"comment","testName":"Lab comment","comment":"Sample slightly hemolyzed"}
                  ]
                }
                """.formatted(patient.getId(), hourAgo()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.panelId", notNullValue()))
                .andExpect(jsonPath("$.templateCode", is("cbc")))
                .andExpect(jsonPath("$.templateVersion", is(1)))
                .andExpect(jsonPath("$.entries", hasSize(4)))
                .andExpect(jsonPath("$.entries[0].key", is("hgb")))
                .andExpect(jsonPath("$.entries[0].flag", is("LOW")))
                .andExpect(jsonPath("$.entries[1].qualifier", is("<")))
                .andExpect(jsonPath("$.entries[2].valueText", is("++")))
                .andExpect(jsonPath("$.entries[2].numericValue", nullValue()))
                .andExpect(jsonPath("$.entries[3].comment", is("Sample slightly hemolyzed")))
                .andReturn();

        String panelId = objectMapper.readTree(created.getResponse().getContentAsString()).get("panelId").asText();

        // Every record carries the same panelId and template data when read back through the vault
        MvcResult read = getPatientResults(patientToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andReturn();
        for (JsonNode record : objectMapper.readTree(read.getResponse().getContentAsString())) {
            assertThat(record.get("panelId").asText()).isEqualTo(panelId);
            assertThat(record.get("templateCode").asText()).isEqualTo("cbc");
            assertThat(record.get("templateVersion").asInt()).isEqualTo(1);
            assertThat(record.get("key").isNull()).isFalse();
        }
    }

    @Test
    @DisplayName("Panel: two panels saved at the same recordedAt keep distinct panelIds")
    void testPanelsAtSameTimestampAreDistinct() throws Exception {
        String patientToken = registerPatient("patient.twopanels@apollo.local");
        String doctorToken = registerDoctor("doctor.twopanels@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        PatientProfile patient = patientProfile(patientToken);
        unlock(patientToken, doctorToken);

        String recordedAt = hourAgo();
        String body = """
                {"patientId":"%s","recordedAt":"%s","templateCode":"cbc","templateVersion":1,
                 "entries":[{"key":"hgb","testName":"Hemoglobin","numericValue":130,"unit":"g/L"}]}
                """.formatted(patient.getId(), recordedAt);

        String first = objectMapper.readTree(postJson("/api/v1/doctor/test-results/panels", doctorToken, body)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("panelId").asText();
        String second = objectMapper.readTree(postJson("/api/v1/doctor/test-results/panels", doctorToken, body)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("panelId").asText();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("Panel: an invalid entry rejects the whole panel with a field path and saves nothing")
    void testPanelValidationRejectsWholePanel() throws Exception {
        String patientToken = registerPatient("patient.panelbad@apollo.local");
        String doctorToken = registerDoctor("doctor.panelbad@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        PatientProfile patient = patientProfile(patientToken);
        unlock(patientToken, doctorToken);

        postJson("/api/v1/doctor/test-results/panels", doctorToken, """
                {"patientId":"%s","recordedAt":"%s",
                 "entries":[
                   {"key":"hgb","testName":"Hemoglobin","numericValue":130,"unit":"g/L"},
                   {"key":"crp","testName":"CRP","qualifier":"<"}
                 ]}
                """.formatted(patient.getId(), hourAgo()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['entries[1].numericValue']", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors['entries[1].qualifier']", notNullValue()));

        postJson("/api/v1/doctor/test-results/panels", doctorToken, """
                {"patientId":"%s","recordedAt":"%s","entries":[]}
                """.formatted(patient.getId(), hourAgo()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.entries", notNullValue()));

        assertThat(labTestResultRepository.count()).isZero();
    }

    @Test
    @DisplayName("Panel: a persistence failure on a later entry rolls back the earlier entries (atomic)")
    void testPanelIsAtomicOnDatabaseFailure() throws Exception {
        String patientToken = registerPatient("patient.atomic@apollo.local");
        String doctorToken = registerDoctor("doctor.atomic@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        PatientProfile patient = patientProfile(patientToken);
        unlock(patientToken, doctorToken);

        // The second value overflows numeric(12,4), so the database rejects it at flush time,
        // after the first entry has already been handed to the persistence context.
        postJson("/api/v1/doctor/test-results/panels", doctorToken, """
                {"patientId":"%s","recordedAt":"%s",
                 "entries":[
                   {"key":"hgb","testName":"Hemoglobin","numericValue":130,"unit":"g/L"},
                   {"key":"big","testName":"Overflow","numericValue":99999999999999,"unit":"g/L"}
                 ]}
                """.formatted(patient.getId(), hourAgo()))
                .andReturn();

        assertThat(labTestResultRepository.count()).isZero();
    }

    @Test
    @DisplayName("Panel: pharmacists, doctors without a session and patients are refused")
    void testPanelAccessRules() throws Exception {
        String patientToken = registerPatient("patient.access@apollo.local");
        String pharmacistToken = registerDoctor("pharm.access@apollo.local", DoctorRole.PHARMACIST);
        String gpToken = registerDoctor("gp.access@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        PatientProfile patient = patientProfile(patientToken);

        String body = """
                {"patientId":"%s","recordedAt":"%s",
                 "entries":[{"key":"hgb","testName":"Hemoglobin","numericValue":130,"unit":"g/L"}]}
                """.formatted(patient.getId(), hourAgo());

        // no session yet
        postJson("/api/v1/doctor/test-results/panels", gpToken, body)
                .andExpect(status().isForbidden());

        // pharmacist, even with a session
        unlock(patientToken, pharmacistToken);
        postJson("/api/v1/doctor/test-results/panels", pharmacistToken, body)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Pharmacists are not authorized to record lab test results.")));

        // patient token on the doctor endpoint
        postJson("/api/v1/doctor/test-results/panels", patientToken, body)
                .andExpect(status().isForbidden());

        // unauthenticated
        mockMvc.perform(post("/api/v1/doctor/test-results/panels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        assertThat(labTestResultRepository.count()).isZero();
    }

    @Test
    @DisplayName("Draft panel is hidden from the patient and visible to its author")
    void testDraftPanel() throws Exception {
        String patientToken = registerPatient("patient.draftpanel@apollo.local");
        String doctorToken = registerDoctor("doctor.draftpanel@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        PatientProfile patient = patientProfile(patientToken);
        unlock(patientToken, doctorToken);

        postJson("/api/v1/doctor/test-results/panels", doctorToken, """
                {"patientId":"%s","recordedAt":"%s","status":"DRAFT",
                 "entries":[{"key":"hgb","testName":"Hemoglobin","numericValue":130,"unit":"g/L"}]}
                """.formatted(patient.getId(), hourAgo()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.entries[0].status", is("DRAFT")));

        getPatientResults(patientToken).andExpect(jsonPath("$", hasSize(0)));
        assertThat(unlockResponse(patientToken, doctorToken).get("testResults")).hasSize(1);
    }

    // ---------------------------------------------------------------- section 3: patient write path

    @Test
    @DisplayName("Patient panel: stored as PATIENT_REPORTED / FINAL, listed with no doctor, visible to a doctor with a session")
    void testPatientReportedPanel() throws Exception {
        String patientToken = registerPatient("patient.own@apollo.local");
        String doctorToken = registerDoctor("doctor.view@apollo.local", DoctorRole.GENERAL_PRACTITIONER);

        postJson("/api/v1/patient/vault/test-results", patientToken, """
                {"recordedAt":"%s","templateCode":"glucose","templateVersion":2,
                 "entries":[
                   {"key":"glu","testName":"Glucose","numericValue":5.4,"unit":"mmol/L"},
                   {"key":"ketones","testName":"Ketones","valueText":"Negative"}
                 ]}
                """.formatted(hourAgo()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.panelId", notNullValue()))
                .andExpect(jsonPath("$.entries", hasSize(2)))
                .andExpect(jsonPath("$.entries[0].source", is("PATIENT_REPORTED")))
                .andExpect(jsonPath("$.entries[0].status", is("FINAL")))
                .andExpect(jsonPath("$.entries[0].doctorId", nullValue()))
                .andExpect(jsonPath("$.entries[0].doctorName", nullValue()));

        getPatientResults(patientToken)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].source", is("PATIENT_REPORTED")));

        JsonNode doctorView = unlockResponse(patientToken, doctorToken);
        assertThat(doctorView.get("testResults")).hasSize(2);
        assertThat(doctorView.get("testResults").get(0).get("source").asText()).isEqualTo("PATIENT_REPORTED");
    }

    @Test
    @DisplayName("Patient panel: drafts are rejected and a doctor token is refused")
    void testPatientPanelRules() throws Exception {
        String patientToken = registerPatient("patient.rules@apollo.local");
        String doctorToken = registerDoctor("doctor.rules@apollo.local", DoctorRole.GENERAL_PRACTITIONER);

        String draft = """
                {"recordedAt":"%s","status":"DRAFT",
                 "entries":[{"key":"glu","testName":"Glucose","numericValue":5.4,"unit":"mmol/L"}]}
                """.formatted(hourAgo());

        postJson("/api/v1/patient/vault/test-results", patientToken, draft)
                .andExpect(status().isBadRequest());

        postJson("/api/v1/patient/vault/test-results", doctorToken, draft.replace("\"DRAFT\"", "\"FINAL\""))
                .andExpect(status().isForbidden());

        assertThat(labTestResultRepository.count()).isZero();
    }

    @Test
    @DisplayName("Patient delete: own patient-reported record is removed; doctor-verified, foreign and unknown ids are refused")
    void testPatientDelete() throws Exception {
        String patientToken = registerPatient("patient.del@apollo.local");
        String otherPatientToken = registerPatient("patient.del.other@apollo.local");
        String doctorToken = registerDoctor("doctor.del@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        PatientProfile patient = patientProfile(patientToken);
        unlock(patientToken, doctorToken);

        // doctor-verified record
        String verifiedId = objectMapper.readTree(postJson("/api/v1/doctor/test-results", doctorToken, """
                {"patientId":"%s","testName":"Glucose","numericValue":5.4,"unit":"mmol/L","recordedAt":"%s"}
                """.formatted(patient.getId(), hourAgo()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();

        // patient-reported record
        String ownId = objectMapper.readTree(postJson("/api/v1/patient/vault/test-results", patientToken, """
                {"recordedAt":"%s","entries":[{"key":"glu","testName":"Glucose","numericValue":6.0,"unit":"mmol/L"}]}
                """.formatted(hourAgo()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("entries").get(0).get("id").asText();

        // doctor-verified: 403
        mockMvc.perform(delete("/api/v1/patient/vault/test-results/" + verifiedId)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden());

        // another patient's record: 404
        mockMvc.perform(delete("/api/v1/patient/vault/test-results/" + ownId)
                        .header("Authorization", "Bearer " + otherPatientToken))
                .andExpect(status().isNotFound());

        // unknown id: 404
        mockMvc.perform(delete("/api/v1/patient/vault/test-results/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isNotFound());

        // own record: 204 and gone
        mockMvc.perform(delete("/api/v1/patient/vault/test-results/" + ownId)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isNoContent());

        getPatientResults(patientToken)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(verifiedId)));
        assertThat(labTestResultRepository.findById(UUID.fromString(ownId))).isEmpty();
    }

    @Test
    @DisplayName("Patient cannot delete a doctor's draft (treated as not found)")
    void testPatientCannotDeleteDoctorDraft() throws Exception {
        String patientToken = registerPatient("patient.deldraft@apollo.local");
        String doctorToken = registerDoctor("doctor.deldraft@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        PatientProfile patient = patientProfile(patientToken);
        unlock(patientToken, doctorToken);

        String draftId = objectMapper.readTree(postJson("/api/v1/doctor/test-results", doctorToken, """
                {"patientId":"%s","testName":"TSH","numericValue":2.1,"unit":"mIU/L","status":"DRAFT","recordedAt":"%s"}
                """.formatted(patient.getId(), hourAgo()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/v1/patient/vault/test-results/" + draftId)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isNotFound());

        assertThat(labTestResultRepository.findById(UUID.fromString(draftId))).isPresent();
    }

    // ---------------------------------------------------------------- section 4: confirmations

    @Test
    @DisplayName("testName is trimmed on write and matched case-insensitively; ordering stays recordedAt ASC")
    void testNameNormalizationAndOrdering() throws Exception {
        String patientToken = registerPatient("patient.names@apollo.local");
        String doctorToken = registerDoctor("doctor.names@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        PatientProfile patient = patientProfile(patientToken);
        unlock(patientToken, doctorToken);

        Instant older = Instant.now().minus(3, ChronoUnit.DAYS);
        Instant newer = Instant.now().minus(1, ChronoUnit.DAYS);

        postJson("/api/v1/doctor/test-results", doctorToken, """
                {"patientId":"%s","testName":"  Vitamin D ","numericValue":40,"unit":"ng/mL","recordedAt":"%s"}
                """.formatted(patient.getId(), newer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.testName", is("Vitamin D")));

        postJson("/api/v1/patient/vault/test-results", patientToken, """
                {"recordedAt":"%s","entries":[{"key":"other","testName":"vitamin d","numericValue":30,"unit":"ng/mL"}]}
                """.formatted(older))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/patient/vault/test-results")
                        .header("Authorization", "Bearer " + patientToken)
                        .param("testName", "  VITAMIN D  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].numericValue", is(30.0)))
                .andExpect(jsonPath("$[1].numericValue", is(40.0)));
    }

    @Test
    @DisplayName("Persisted defaults: status FINAL and source DOCTOR_VERIFIED on the entity")
    void testEntityDefaults() throws Exception {
        String patientToken = registerPatient("patient.defaults@apollo.local");
        String doctorToken = registerDoctor("doctor.defaults@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        PatientProfile patient = patientProfile(patientToken);
        DoctorProfile doctor = doctorProfile(doctorToken);
        unlock(patientToken, doctorToken);

        postJson("/api/v1/doctor/test-results", doctorToken, """
                {"patientId":"%s","testName":"Glucose","numericValue":5.4,"unit":"mmol/L","recordedAt":"%s"}
                """.formatted(patient.getId(), hourAgo()))
                .andExpect(status().isCreated());

        var stored = labTestResultRepository.findAll();
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getStatus()).isEqualTo(LabResultStatus.FINAL);
        assertThat(stored.get(0).getSource()).isEqualTo(LabResultSource.DOCTOR_VERIFIED);
        assertThat(stored.get(0).getDoctor().getId()).isEqualTo(doctor.getId());
    }

    @Test
    @DisplayName("OpenAPI: numericValue is optional, qualifier is a '<' / '>' enum, and the new endpoints are published")
    void testOpenApiShape() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode doc = objectMapper.readTree(result.getResponse().getContentAsString());

        JsonNode request = doc.at("/components/schemas/CreateLabTestResultRequest");
        assertThat(request.isMissingNode()).isFalse();
        assertThat(request.get("properties").has("valueText")).isTrue();
        assertThat(request.get("properties").has("status")).isTrue();

        JsonNode required = request.get("required");
        assertThat(required).isNotNull();
        java.util.Set<String> requiredNames = new java.util.HashSet<>();
        required.forEach(n -> requiredNames.add(n.asText()));
        assertThat(requiredNames).contains("patientId", "testName", "recordedAt");
        assertThat(requiredNames).doesNotContain("numericValue", "unit", "valueText", "qualifier", "status");

        JsonNode qualifier = request.at("/properties/qualifier");
        String qualifierJson = qualifier.toString();
        // inline enum or a $ref to an enum schema; the symbols must be < and >
        if (qualifier.has("$ref")) {
            String name = qualifier.get("$ref").asText().substring(qualifier.get("$ref").asText().lastIndexOf('/') + 1);
            qualifierJson = doc.at("/components/schemas/" + name).toString();
        }
        assertThat(qualifierJson).contains("\"<\"").contains("\">\"");

        assertThat(doc.at("/paths").has("/api/v1/doctor/test-results/panels")).isTrue();
        assertThat(doc.at("/paths/~1api~1v1~1patient~1vault~1test-results").has("post")).isTrue();
        assertThat(doc.at("/paths/~1api~1v1~1patient~1vault~1test-results~1{testResultId}").has("delete")).isTrue();
    }
}

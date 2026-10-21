package com.apollo.controller;

import com.apollo.domain.entity.PatientProfile;
import com.apollo.domain.enums.DoctorRole;
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
import com.apollo.repository.VisionPrescriptionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VisionPrescriptionIntegrationTests {

    private static final String ISSUE_URL = "/api/v1/doctor/vision-prescriptions";
    private static final String LIST_URL = "/api/v1/patient/vault/vision-prescriptions";

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

    @Autowired
    private VisionPrescriptionRepository visionPrescriptionRepository;

    @BeforeEach
    void setUp() {
        cleanUp();
    }

    // The other integration test classes share this database and do not know about this table, so rows
    // referencing patients and doctors must not outlive this class.
    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        visionPrescriptionRepository.deleteAll();
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
                .specialty("Ophthalmology")
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

    private JsonNode unlock(String patientToken, String doctorToken) throws Exception {
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

    private ResultActions issue(String token, String json) throws Exception {
        return mockMvc.perform(post(ISSUE_URL)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions listAsPatient(String patientToken) throws Exception {
        return mockMvc.perform(get(LIST_URL).header("Authorization", "Bearer " + patientToken));
    }

    private static String astigmatismExample(UUID patientId) {
        return """
                {
                  "patientId": "%s",
                  "rightEye": { "sphere": -2.5, "cylinder": -0.75, "axis": 180, "add": 1.5 },
                  "leftEye": { "sphere": -1.75, "add": 1.5 },
                  "pupillaryDistanceMm": 63,
                  "notes": "Progressive lenses.",
                  "expiresAt": "2028-10-13T20:59:00.000Z"
                }
                """.formatted(patientId);
    }

    /** A request with one valid right eye (sphere 1.0), so that tests vary a single rule at a time. */
    private static String withRightEye(UUID patientId, String rightEyeJson) {
        return """
                {"patientId":"%s","rightEye":%s}
                """.formatted(patientId, rightEyeJson);
    }

    // ---------------------------------------------------------------- issuing

    @Test
    @DisplayName("A general practitioner with an active session issues the astigmatism example; the server sets id, doctor and issuedAt")
    void issueAstigmatismExample() throws Exception {
        String patientToken = registerPatient("patient.astig@apollo.local");
        String doctorToken = registerDoctor("doctor.astig@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        PatientProfile patient = patientProfile(patientToken);
        unlock(patientToken, doctorToken);

        issue(doctorToken, astigmatismExample(patient.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.patientId", is(patient.getId().toString())))
                .andExpect(jsonPath("$.doctorId", is(profileId(doctorToken).toString())))
                .andExpect(jsonPath("$.doctorName", is("Dr. Gregory House")))
                .andExpect(jsonPath("$.issuedAt", notNullValue()))
                .andExpect(jsonPath("$.expiresAt", is("2028-10-13T20:59:00Z")))
                .andExpect(jsonPath("$.rightEye.sphere", is(-2.5)))
                .andExpect(jsonPath("$.rightEye.cylinder", is(-0.75)))
                .andExpect(jsonPath("$.rightEye.axis", is(180)))
                .andExpect(jsonPath("$.rightEye.add", is(1.5)))
                .andExpect(jsonPath("$.leftEye.sphere", is(-1.75)))
                .andExpect(jsonPath("$.leftEye", not(hasKey("cylinder"))))
                .andExpect(jsonPath("$.leftEye", not(hasKey("axis"))))
                .andExpect(jsonPath("$.leftEye.add", is(1.5)))
                .andExpect(jsonPath("$.pupillaryDistanceMm", is(63.0)))
                .andExpect(jsonPath("$.notes", is("Progressive lenses.")))
                .andExpect(jsonPath("$", not(hasKey("encounterId"))));

        assertThat(visionPrescriptionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("One eye only: a plano eye keeps sphere 0, an eye that is not prescribed is an empty object")
    void planoAndUnprescribedEye() throws Exception {
        String patientToken = registerPatient("patient.plano@apollo.local");
        String doctorToken = registerDoctor("doctor.plano@apollo.local", DoctorRole.SPECIALIST);
        PatientProfile patient = patientProfile(patientToken);
        unlock(patientToken, doctorToken);

        issue(doctorToken, """
                {"patientId":"%s","rightEye":{"sphere":0.5},"leftEye":{"sphere":0}}
                """.formatted(patient.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rightEye.sphere", is(0.5)))
                .andExpect(jsonPath("$.leftEye.sphere", is(0.0)))
                .andExpect(jsonPath("$", not(hasKey("pupillaryDistanceMm"))))
                .andExpect(jsonPath("$", not(hasKey("notes"))))
                .andExpect(jsonPath("$", not(hasKey("expiresAt"))));

        issue(doctorToken, """
                {"patientId":"%s","rightEye":{"sphere":-1}}
                """.formatted(patient.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.leftEye", anEmptyMap()));

        // an explicit empty object is "not prescribed" as well
        issue(doctorToken, """
                {"patientId":"%s","rightEye":{},"leftEye":{"sphere":2}}
                """.formatted(patient.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rightEye", anEmptyMap()))
                .andExpect(jsonPath("$.leftEye.sphere", is(2.0)));
    }

    @Test
    @DisplayName("Powers round-trip exactly through storage, including the patient list")
    void powersRoundTrip() throws Exception {
        String patientToken = registerPatient("patient.exact@apollo.local");
        String doctorToken = registerDoctor("doctor.exact@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        PatientProfile patient = patientProfile(patientToken);
        unlock(patientToken, doctorToken);

        issue(doctorToken, """
                {"patientId":"%s","rightEye":{"sphere":-2.25,"cylinder":-0.25,"axis":0},
                 "leftEye":{"sphere":-20,"cylinder":8,"axis":180,"add":4},"pupillaryDistanceMm":62.5}
                """.formatted(patient.getId()))
                .andExpect(status().isCreated());

        listAsPatient(patientToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].rightEye.sphere", is(-2.25)))
                .andExpect(jsonPath("$[0].rightEye.cylinder", is(-0.25)))
                .andExpect(jsonPath("$[0].rightEye.axis", is(0)))
                .andExpect(jsonPath("$[0].leftEye.sphere", is(-20.0)))
                .andExpect(jsonPath("$[0].leftEye.cylinder", is(8.0)))
                .andExpect(jsonPath("$[0].leftEye.axis", is(180)))
                .andExpect(jsonPath("$[0].leftEye.add", is(4.0)))
                .andExpect(jsonPath("$[0].pupillaryDistanceMm", is(62.5)));
    }

    @Test
    @DisplayName("A zero cylinder is stored as absent together with any axis sent with it; -0 is treated as 0")
    void zeroCylinderIsNormalisedAway() throws Exception {
        String patientToken = registerPatient("patient.zero@apollo.local");
        String doctorToken = registerDoctor("doctor.zero@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        PatientProfile patient = patientProfile(patientToken);
        unlock(patientToken, doctorToken);

        issue(doctorToken, withRightEye(patient.getId(), "{\"sphere\":-1.5,\"cylinder\":0,\"axis\":90}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rightEye.sphere", is(-1.5)))
                .andExpect(jsonPath("$.rightEye", not(hasKey("cylinder"))))
                .andExpect(jsonPath("$.rightEye", not(hasKey("axis"))));

        issue(doctorToken, withRightEye(patient.getId(), "{\"sphere\":-0,\"cylinder\":-0.0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rightEye.sphere", is(0.0)))
                .andExpect(jsonPath("$.rightEye", not(hasKey("cylinder"))));

        // a whole-number axis written with a decimal point is accepted
        issue(doctorToken, withRightEye(patient.getId(), "{\"sphere\":1,\"cylinder\":-1,\"axis\":90.0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rightEye.axis", is(90)));
    }

    // ---------------------------------------------------------------- validation

    @Test
    @DisplayName("Validation: every rule answers 400 with a readable message and stores nothing")
    void validationRules() throws Exception {
        String patientToken = registerPatient("patient.rules@apollo.local");
        String doctorToken = registerDoctor("doctor.rules@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        PatientProfile patient = patientProfile(patientToken);
        UUID id = patient.getId();
        unlock(patientToken, doctorToken);

        // at least one eye
        issue(doctorToken, "{\"patientId\":\"%s\"}".formatted(id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Enter the prescription for at least one eye.")));
        issue(doctorToken, "{\"patientId\":\"%s\",\"rightEye\":{},\"leftEye\":{}}".formatted(id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Enter the prescription for at least one eye.")));

        // sphere required for an eye that has any value
        issue(doctorToken, withRightEye(id, "{\"add\":1.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Enter the sphere, or 0 for plano.")));

        // sphere range and step
        issue(doctorToken, withRightEye(id, "{\"sphere\":20.25}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("The sphere must be between -20.00 and +20.00.")));
        issue(doctorToken, withRightEye(id, "{\"sphere\":-20.25}"))
                .andExpect(status().isBadRequest());
        issue(doctorToken, withRightEye(id, "{\"sphere\":-1.3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Use steps of 0.25.")));

        // cylinder range and step, both signs valid
        issue(doctorToken, withRightEye(id, "{\"sphere\":1,\"cylinder\":8.25,\"axis\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("The cylinder must be between -8.00 and +8.00.")));
        issue(doctorToken, withRightEye(id, "{\"sphere\":1,\"cylinder\":-0.6,\"axis\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Use steps of 0.25.")));
        issue(doctorToken, withRightEye(id, "{\"sphere\":1,\"cylinder\":0.75,\"axis\":10}"))
                .andExpect(status().isCreated());

        // axis: required with a cylinder, rejected without one, whole number within 0..180
        issue(doctorToken, withRightEye(id, "{\"sphere\":1,\"cylinder\":-0.75}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Enter the axis for this cylinder.")));
        issue(doctorToken, withRightEye(id, "{\"sphere\":1,\"axis\":90}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("The axis only applies together with a cylinder.")));
        issue(doctorToken, withRightEye(id, "{\"sphere\":1,\"cylinder\":-1,\"axis\":181}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("The axis must be a whole number from 0 to 180.")));
        issue(doctorToken, withRightEye(id, "{\"sphere\":1,\"cylinder\":-1,\"axis\":-1}"))
                .andExpect(status().isBadRequest());
        issue(doctorToken, withRightEye(id, "{\"sphere\":1,\"cylinder\":-1,\"axis\":90.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("The axis must be a whole number from 0 to 180.")));

        // add: positive, 0.25..4, quarter steps
        issue(doctorToken, withRightEye(id, "{\"sphere\":1,\"add\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("The add must be between +0.25 and +4.00.")));
        issue(doctorToken, withRightEye(id, "{\"sphere\":1,\"add\":-1}"))
                .andExpect(status().isBadRequest());
        issue(doctorToken, withRightEye(id, "{\"sphere\":1,\"add\":4.25}"))
                .andExpect(status().isBadRequest());
        issue(doctorToken, withRightEye(id, "{\"sphere\":1,\"add\":1.1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Use steps of 0.25.")));

        // an exotic exponent is rejected by the range check without being expanded
        issue(doctorToken, withRightEye(id, "{\"sphere\":1E+999999999}"))
                .andExpect(status().isBadRequest());

        // the left eye is validated with the same rules
        issue(doctorToken, "{\"patientId\":\"%s\",\"leftEye\":{\"sphere\":3,\"axis\":10}}".formatted(id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("The axis only applies together with a cylinder.")));

        // pupillary distance
        issue(doctorToken, "{\"patientId\":\"%s\",\"rightEye\":{\"sphere\":1},\"pupillaryDistanceMm\":34.9}".formatted(id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("The pupillary distance should be between 35 and 90 mm.")));
        issue(doctorToken, "{\"patientId\":\"%s\",\"rightEye\":{\"sphere\":1},\"pupillaryDistanceMm\":90.1}".formatted(id))
                .andExpect(status().isBadRequest());

        // notes
        issue(doctorToken, "{\"patientId\":\"%s\",\"rightEye\":{\"sphere\":1},\"notes\":\"%s\"}"
                .formatted(id, "x".repeat(1001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Notes can be at most 1000 characters.")));

        // expiresAt must be a date-time
        issue(doctorToken, "{\"patientId\":\"%s\",\"rightEye\":{\"sphere\":1},\"expiresAt\":\"tomorrow\"}".formatted(id))
                .andExpect(status().isBadRequest());

        // patientId
        issue(doctorToken, "{\"rightEye\":{\"sphere\":1}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Patient ID is required.")));

        // only the accepted 0.75 cylinder above was stored
        assertThat(visionPrescriptionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Boundaries are accepted; a past expiresAt is accepted; notes are trimmed and 1000 characters fit")
    void boundariesAndLenientFields() throws Exception {
        String patientToken = registerPatient("patient.edge@apollo.local");
        String doctorToken = registerDoctor("doctor.edge@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        PatientProfile patient = patientProfile(patientToken);
        unlock(patientToken, doctorToken);

        issue(doctorToken, """
                {"patientId":"%s","rightEye":{"sphere":20,"add":0.25},"leftEye":{"sphere":-20,"cylinder":-8,"axis":180},
                 "pupillaryDistanceMm":35,"notes":"  %s  ","expiresAt":"2020-01-01T00:00:00Z"}
                """.formatted(patient.getId(), "n".repeat(1000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt", is("2020-01-01T00:00:00Z")))
                .andExpect(jsonPath("$.notes", is("n".repeat(1000))));

        issue(doctorToken, """
                {"patientId":"%s","rightEye":{"sphere":1},"pupillaryDistanceMm":90,"notes":"   "}
                """.formatted(patient.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", not(hasKey("notes"))));
    }

    // ---------------------------------------------------------------- authorization

    @Test
    @DisplayName("Lab technicians and pharmacists cannot issue: 403, nothing stored")
    void otherDoctorRolesAreForbidden() throws Exception {
        String patientToken = registerPatient("patient.roles@apollo.local");
        String labToken = registerDoctor("lab.roles@apollo.local", DoctorRole.LAB_TECHNICIAN);
        String pharmacistToken = registerDoctor("pharm.roles@apollo.local", DoctorRole.PHARMACIST);
        PatientProfile patient = patientProfile(patientToken);
        unlock(patientToken, labToken);
        unlock(patientToken, pharmacistToken);

        issue(labToken, astigmatismExample(patient.getId())).andExpect(status().isForbidden());
        issue(pharmacistToken, astigmatismExample(patient.getId())).andExpect(status().isForbidden());

        assertThat(visionPrescriptionRepository.count()).isZero();
    }

    @Test
    @DisplayName("A doctor without an active session for the patient gets 403, not 401; an unknown patient is 404")
    void sessionAndPatientChecks() throws Exception {
        String patientToken = registerPatient("patient.session@apollo.local");
        String unlockedToken = registerDoctor("doctor.unlocked@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        String strangerToken = registerDoctor("doctor.stranger@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        PatientProfile patient = patientProfile(patientToken);
        unlock(patientToken, unlockedToken);

        issue(strangerToken, astigmatismExample(patient.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", notNullValue()));

        issue(unlockedToken, astigmatismExample(UUID.randomUUID()))
                .andExpect(status().isNotFound());

        assertThat(visionPrescriptionRepository.count()).isZero();
    }

    @Test
    @DisplayName("Patients cannot issue, and a missing token is 401")
    void patientsAndAnonymousCannotIssue() throws Exception {
        String patientToken = registerPatient("patient.noissue@apollo.local");
        PatientProfile patient = patientProfile(patientToken);

        issue(patientToken, astigmatismExample(patient.getId())).andExpect(status().isForbidden());

        mockMvc.perform(post(ISSUE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(astigmatismExample(patient.getId())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(LIST_URL)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("doctorId, doctorName and issuedAt sent by a client are ignored")
    void serverSetFieldsAreNotTakenFromTheBody() throws Exception {
        String patientToken = registerPatient("patient.forge@apollo.local");
        String doctorToken = registerDoctor("doctor.forge@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        PatientProfile patient = patientProfile(patientToken);
        unlock(patientToken, doctorToken);

        issue(doctorToken, """
                {"patientId":"%s","rightEye":{"sphere":1},"doctorId":"%s","doctorName":"Dr. Someone Else",
                 "issuedAt":"2001-01-01T00:00:00Z","id":"%s"}
                """.formatted(patient.getId(), UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.doctorId", is(profileId(doctorToken).toString())))
                .andExpect(jsonPath("$.doctorName", is("Dr. Gregory House")))
                .andExpect(jsonPath("$.issuedAt", not(is("2001-01-01T00:00:00Z"))));
    }

    @Test
    @DisplayName("encounterId is optional; one that belongs to another patient is rejected, an unknown one is 404")
    void encounterLink() throws Exception {
        String patientToken = registerPatient("patient.enc@apollo.local");
        String otherPatientToken = registerPatient("patient.enc.other@apollo.local");
        String doctorToken = registerDoctor("doctor.enc@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        PatientProfile patient = patientProfile(patientToken);
        PatientProfile otherPatient = patientProfile(otherPatientToken);
        unlock(patientToken, doctorToken);
        unlock(otherPatientToken, doctorToken);

        String encounterJson = """
                {"patientId":"%s","diagnosis":"Myopia","clinicalNotes":"Eye exam","encounterDate":"%s"}
                """;
        MvcResult own = mockMvc.perform(post("/api/v1/doctor/encounters")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(encounterJson.formatted(patient.getId(), LocalDate.now())))
                .andExpect(status().isCreated())
                .andReturn();
        String ownEncounterId = objectMapper.readTree(own.getResponse().getContentAsString()).get("id").asText();

        issue(doctorToken, """
                {"patientId":"%s","encounterId":"%s","rightEye":{"sphere":-1}}
                """.formatted(patient.getId(), ownEncounterId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.encounterId", is(ownEncounterId)));

        issue(doctorToken, """
                {"patientId":"%s","encounterId":"%s","rightEye":{"sphere":-1}}
                """.formatted(otherPatient.getId(), ownEncounterId))
                .andExpect(status().isBadRequest());

        issue(doctorToken, """
                {"patientId":"%s","encounterId":"%s","rightEye":{"sphere":-1}}
                """.formatted(patient.getId(), UUID.randomUUID()))
                .andExpect(status().isNotFound());

        assertThat(visionPrescriptionRepository.count()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- reading

    @Test
    @DisplayName("Patient list: own records only, newest first, [] when none")
    void patientListIsScopedAndOrdered() throws Exception {
        String patientToken = registerPatient("patient.list@apollo.local");
        String otherPatientToken = registerPatient("patient.list.other@apollo.local");
        String doctorToken = registerDoctor("doctor.list@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        PatientProfile patient = patientProfile(patientToken);
        PatientProfile otherPatient = patientProfile(otherPatientToken);

        listAsPatient(patientToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        unlock(patientToken, doctorToken);
        unlock(otherPatientToken, doctorToken);

        // Explicit timestamps test ordering without depending on the host clock's resolution.
        Instant firstIssuedAt = Instant.now().minusSeconds(60);
        int sequence = 0;
        for (var entry : java.util.List.of(
                java.util.Map.entry(patient.getId(), -1), java.util.Map.entry(otherPatient.getId(), 7),
                java.util.Map.entry(patient.getId(), -2), java.util.Map.entry(patient.getId(), -3))) {
            MvcResult issued = issue(doctorToken, withRightEye(entry.getKey(), "{\"sphere\":" + entry.getValue() + "}"))
                    .andExpect(status().isCreated()).andReturn();
            UUID prescriptionId = UUID.fromString(objectMapper.readTree(issued.getResponse().getContentAsString())
                    .get("id").asText());
            var prescription = visionPrescriptionRepository.findById(prescriptionId).orElseThrow();
            prescription.setIssuedAt(firstIssuedAt.plusSeconds(sequence++));
            visionPrescriptionRepository.save(prescription);
        }

        listAsPatient(patientToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].rightEye.sphere", is(-3.0)))
                .andExpect(jsonPath("$[1].rightEye.sphere", is(-2.0)))
                .andExpect(jsonPath("$[2].rightEye.sphere", is(-1.0)))
                .andExpect(jsonPath("$[0].patientId", is(patient.getId().toString())))
                .andExpect(jsonPath("$[0].doctorName", is("Dr. Gregory House")));

        listAsPatient(otherPatientToken)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].rightEye.sphere", is(7.0)));
    }

    @Test
    @DisplayName("The patient list endpoint is patient-only")
    void patientListRejectsDoctors() throws Exception {
        String doctorToken = registerDoctor("doctor.nolist@apollo.local", DoctorRole.GENERAL_PRACTITIONER);

        mockMvc.perform(get(LIST_URL).header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Vault unlock includes visionPrescriptions for every doctor role, newest first, with the other fields unchanged")
    void unlockIncludesVisionPrescriptions() throws Exception {
        String patientToken = registerPatient("patient.unlock@apollo.local");
        String gpToken = registerDoctor("gp.unlock@apollo.local", DoctorRole.GENERAL_PRACTITIONER);
        String labToken = registerDoctor("lab.unlock@apollo.local", DoctorRole.LAB_TECHNICIAN);
        String pharmacistToken = registerDoctor("pharm.unlock@apollo.local", DoctorRole.PHARMACIST);
        PatientProfile patient = patientProfile(patientToken);

        // before anything is issued the field is present and empty
        JsonNode before = unlock(patientToken, gpToken);
        assertThat(before.get("visionPrescriptions").isArray()).isTrue();
        assertThat(before.get("visionPrescriptions")).isEmpty();

        MvcResult olderResult = issue(gpToken, withRightEye(patient.getId(), "{\"sphere\":-1}"))
                .andExpect(status().isCreated()).andReturn();
        UUID olderId = UUID.fromString(objectMapper.readTree(olderResult.getResponse().getContentAsString())
                .get("id").asText());
        var olderPrescription = visionPrescriptionRepository.findById(olderId).orElseThrow();
        olderPrescription.setIssuedAt(Instant.now().minusSeconds(60));
        visionPrescriptionRepository.save(olderPrescription);
        issue(gpToken, astigmatismExample(patient.getId())).andExpect(status().isCreated());

        JsonNode gpView = unlock(patientToken, gpToken);
        assertThat(gpView.get("visionPrescriptions")).hasSize(2);
        assertThat(gpView.get("visionPrescriptions").get(0).get("rightEye").get("cylinder").decimalValue())
                .isEqualByComparingTo("-0.75");
        assertThat(gpView.get("visionPrescriptions").get(1).get("rightEye").get("sphere").decimalValue())
                .isEqualByComparingTo("-1");
        for (String existing : new String[]{"patient", "allergies", "chronicConditions", "allConditions",
                "encounterHistory", "activeSessionId", "sessionExpiresAt", "prescriptions", "testResults"}) {
            assertThat(gpView.has(existing)).as(existing).isTrue();
        }

        assertThat(unlock(patientToken, labToken).get("visionPrescriptions")).hasSize(2);
        assertThat(unlock(patientToken, pharmacistToken).get("visionPrescriptions")).hasSize(2);
    }
}

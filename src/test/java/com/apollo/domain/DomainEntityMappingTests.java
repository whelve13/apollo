package com.apollo.domain;

import com.apollo.domain.entity.AccessGrant;
import com.apollo.domain.entity.ClinicalEncounter;
import com.apollo.domain.entity.DoctorProfile;
import com.apollo.domain.entity.HealthCondition;
import com.apollo.domain.entity.PatientProfile;
import com.apollo.domain.entity.Prescription;
import com.apollo.domain.entity.User;
import com.apollo.domain.enums.HealthConditionType;
import com.apollo.domain.enums.PrescriptionStatus;
import com.apollo.domain.enums.Role;
import com.apollo.domain.enums.SourceType;
import com.apollo.repository.AccessGrantRepository;
import com.apollo.repository.ClinicalEncounterRepository;
import com.apollo.repository.DoctorProfileRepository;
import com.apollo.repository.HealthConditionRepository;
import com.apollo.repository.PatientProfileRepository;
import com.apollo.repository.PrescriptionRepository;
import com.apollo.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DomainEntityMappingTests {

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
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private AccessGrantRepository accessGrantRepository;

    @Test
    @DisplayName("Should persist Patient and Doctor profiles linked to User accounts")
    void shouldPersistPatientAndDoctorProfiles() {
        // Patient User
        User patientUser = User.builder()
                .email("patient@apollo.local")
                .passwordHash("hashed_pwd_123")
                .role(Role.ROLE_PATIENT)
                .build();
        userRepository.save(patientUser);

        PatientProfile patient = PatientProfile.builder()
                .user(patientUser)
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1990, 5, 15))
                .bloodType("O+")
                .build();
        patientProfileRepository.save(patient);

        // Doctor User
        User doctorUser = User.builder()
                .email("dr.house@apollo.local")
                .passwordHash("hashed_pwd_456")
                .role(Role.ROLE_DOCTOR)
                .build();
        userRepository.save(doctorUser);

        DoctorProfile doctor = DoctorProfile.builder()
                .user(doctorUser)
                .firstName("Gregory")
                .lastName("House")
                .licenseNumber("MD-998822")
                .specialty("Diagnostics")
                .build();
        doctorProfileRepository.save(doctor);

        assertThat(patient.getId()).isNotNull();
        assertThat(patient.getCreatedAt()).isNotNull();
        assertThat(doctor.getId()).isNotNull();
        assertThat(doctor.getLicenseNumber()).isEqualTo("MD-998822");
        assertThat(doctor.getDoctorRole()).isEqualTo(com.apollo.domain.enums.DoctorRole.GENERAL_PRACTITIONER);
    }

    @Test
    @DisplayName("Should record Patient-Declared baseline health condition")
    void shouldRecordPatientDeclaredCondition() {
        User user = userRepository.save(User.builder()
                .email("patient2@apollo.local")
                .passwordHash("hash")
                .role(Role.ROLE_PATIENT)
                .build());

        PatientProfile patient = patientProfileRepository.save(PatientProfile.builder()
                .user(user)
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 10, 20))
                .build());

        HealthCondition condition = HealthCondition.builder()
                .patient(patient)
                .title("Penicillin Allergy")
                .type(HealthConditionType.ALLERGY)
                .sourceType(SourceType.PATIENT_DECLARED)
                .dateRecorded(LocalDate.now())
                .build();
        healthConditionRepository.save(condition);

        assertThat(condition.getId()).isNotNull();
        assertThat(condition.getSourceType()).isEqualTo(SourceType.PATIENT_DECLARED);
        assertThat(condition.getType()).isEqualTo(HealthConditionType.ALLERGY);
    }

    @Test
    @DisplayName("Should manage Access Grants with 15 minute expiration and validity checks")
    void shouldManageAccessGrants() {
        User user = userRepository.save(User.builder()
                .email("patient3@apollo.local")
                .passwordHash("hash")
                .role(Role.ROLE_PATIENT)
                .build());

        PatientProfile patient = patientProfileRepository.save(PatientProfile.builder()
                .user(user)
                .firstName("Alice")
                .lastName("Walker")
                .dateOfBirth(LocalDate.of(1992, 1, 10))
                .build());

        Instant now = Instant.now();
        AccessGrant grant = AccessGrant.builder()
                .patient(patient)
                .accessCode("847291")
                .expiresAt(now.plus(15, ChronoUnit.MINUTES))
                .isUsed(false)
                .build();
        accessGrantRepository.save(grant);

        assertThat(grant.getId()).isNotNull();
        assertThat(grant.isValid()).isTrue();
        assertThat(grant.isExpired()).isFalse();

        // Check finding active token
        var found = accessGrantRepository.findByAccessCodeAndIsUsedFalseAndExpiresAtAfter("847291", now);
        assertThat(found).isPresent();
    }

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    @DisplayName("Should create Clinical Encounter and enforce append-only immutability")
    void shouldEnforceClinicalEncounterImmutability() {
        User pUser = userRepository.save(User.builder()
                .email("patient4@apollo.local")
                .passwordHash("hash")
                .role(Role.ROLE_PATIENT)
                .build());
        PatientProfile patient = patientProfileRepository.save(PatientProfile.builder()
                .user(pUser)
                .firstName("Bob")
                .lastName("Brown")
                .dateOfBirth(LocalDate.of(1978, 3, 12))
                .build());

        User dUser = userRepository.save(User.builder()
                .email("dr.smith@apollo.local")
                .passwordHash("hash")
                .role(Role.ROLE_DOCTOR)
                .build());
        DoctorProfile doctor = doctorProfileRepository.save(DoctorProfile.builder()
                .user(dUser)
                .firstName("John")
                .lastName("Smith")
                .licenseNumber("MD-12345")
                .specialty("General Medicine")
                .build());

        ClinicalEncounter encounter = ClinicalEncounter.builder()
                .patient(patient)
                .doctor(doctor)
                .encounterDate(LocalDate.now())
                .diagnosis("Acute Bronchitis")
                .clinicalNotes("Patient presented with severe cough and mild fever. Prescribed inhaler.")
                .build();
        clinicalEncounterRepository.saveAndFlush(encounter);

        assertThat(encounter.getId()).isNotNull();
        assertThat(encounter.getCreatedAt()).isNotNull();

        // Attempting to modify should be ignored by JPA update (updatable = false)
        encounter.setDiagnosis("Tampered Diagnosis");
        clinicalEncounterRepository.saveAndFlush(encounter);

        // Evict from first-level cache to verify persistence state in database
        entityManager.clear();

        ClinicalEncounter persisted = clinicalEncounterRepository.findById(encounter.getId()).orElseThrow();
        assertThat(persisted.getDiagnosis()).isEqualTo("Acute Bronchitis");
    }

    @Test
    @DisplayName("Should persist Prescription linked to patient, doctor, and optional encounter")
    void shouldPersistPrescription() {
        User pUser = userRepository.save(User.builder()
                .email("patient5@apollo.local")
                .passwordHash("hash")
                .role(Role.ROLE_PATIENT)
                .build());
        PatientProfile patient = patientProfileRepository.save(PatientProfile.builder()
                .user(pUser)
                .firstName("Carol")
                .lastName("White")
                .dateOfBirth(LocalDate.of(1995, 7, 22))
                .build());

        User dUser = userRepository.save(User.builder()
                .email("dr.jones@apollo.local")
                .passwordHash("hash")
                .role(Role.ROLE_DOCTOR)
                .build());
        DoctorProfile doctor = doctorProfileRepository.save(DoctorProfile.builder()
                .user(dUser)
                .firstName("Sarah")
                .lastName("Jones")
                .licenseNumber("MD-67890")
                .specialty("Cardiology")
                .build());

        Instant now = Instant.now();
        Prescription prescription = Prescription.builder()
                .patient(patient)
                .doctor(doctor)
                .medicationName("Amoxicillin")
                .dosage("500mg")
                .instructions("Take 1 capsule every 8 hours with meals for 10 days.")
                .status(PrescriptionStatus.ACTIVE)
                .issuedAt(now)
                .expiresAt(now.plus(30, ChronoUnit.DAYS))
                .build();
        prescriptionRepository.save(prescription);

        assertThat(prescription.getId()).isNotNull();
        assertThat(prescription.getEncounter()).isNull(); // Decoupled prescription
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.ACTIVE);
    }
}

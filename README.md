# Apollo - Medical Records Vault Backend

[![Java](https://img.shields.io/badge/Java-25%20LTS-orange.svg)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15%2B-blue.svg)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Apollo is a secure, shared medical records vault backend engineered to replace traditional paper medical booklets with a patient-owned digital solution.

---

## Core Product Architecture & Features

1. **Patient Vault Ownership**:
   - Patients own and initialize their health vault with baseline data (allergies, chronic conditions, past medical history).
   - Baseline records are tagged as `PATIENT_DECLARED`.
   - Patients generate temporary 6-digit access tokens (strictly valid for 15 minutes) to present to clinicians during consultations.
   - Each accepted PIN creates a persistent connection between that patient and the authenticated doctor. A doctor can keep multiple patient connections at once and restore them after reloading or signing in again.
   - Connections expire at the original PIN's expiration time. Generating a replacement PIN or explicitly invalidating access revokes existing connections immediately; unlocking never starts a separate 24-hour window.
2. **Doctor Append-Only Interface**:
   - Clinicians authenticate, validate the patient's temporary access token, and unlock append rights.
   - Encounters (diagnoses, consultation notes, examination details) are **strictly immutable and append-only** once saved.
3. **Decoupled Prescriptions**:
   - Prescriptions are decoupled from the general encounter timeline for fast pharmacy access.
   - Doctors can issue prescriptions during an encounter or independently.
   - Prescriptions track status (`ACTIVE`, `FULFILLED`, `CANCELLED`), dosage, instructions, issue date, and expiration.

---

## Connected Patient API

All doctor routes below require a doctor JWT; doctor identity comes from that JWT.

| Method | Route | Behavior |
| :--- | :--- | :--- |
| POST | `/api/v1/doctor/vault/unlock` | Accepts `{ "accessCode": "123456" }`, consumes the single-use PIN, and saves the doctor/patient/grant connection. |
| GET | `/api/v1/doctor/vault/sessions` | Restores all currently authorized patient vaults for this doctor, using the same role-filtered response as unlock. PINs are never returned. |
| GET | `/api/v1/doctor/vault/patients/{patientId}` | Refreshes one connected vault; returns 403 when its connection is absent, expired, or revoked. |
| DELETE | `/api/v1/doctor/vault/patients/{patientId}` | Disconnects only this doctor's connection to this patient. |
| DELETE | `/api/v1/patient/vault/access-grants` | Patient JWT required. Invalidates this patient's PINs and every connection created from them. Idempotent. |

The frontend restores connections from the server on login/reload and rechecks every 15 seconds and on browser focus. It retains no PINs or patient records in browser storage. Backend access checks apply immediately to subsequent requests after expiration/revocation, even if a browser still displays an earlier snapshot.

The existing `POST /api/v1/patient/vault/access-grants` also revokes older connections before issuing a new PIN. Role changes disconnect the doctor's existing vaults and require new patient consent.

Deployment adds nullable `access_grant_id` on `active_vault_sessions` and `revoked_at` on `access_grants` through the existing Hibernate schema update configuration. Legacy sessions without an associated grant are intentionally excluded from authorization; those doctors must unlock again.

---

## Tech Stack

- **Framework** - Spring Boot 3.4.3
- **Language** - Java 25 LTS
- **Build Tool** - Maven (`./mvnw`)
- **Persistence** - Spring Data JPA / Hibernate
- **Database** - PostgreSQL (`apollo_db`)
- **Security** - Spring Security (Stateless JWT authentication)
- **Validation** - Jakarta Validation (`jakarta.validation`)
- **API Docs** - Springdoc OpenAPI / Swagger UI 3 (`springdoc-openapi-starter-webmvc-ui`)
- **Boilerplate Reduction** - Project Lombok

---

## Domain Entities

| Entity | Description |
| :--- | :--- |
| `User` | Authentication credentials, email, hashed password, and role (`ROLE_PATIENT`, `ROLE_DOCTOR`). |
| `PatientProfile` | Patient demographics, birth date, blood type, and vault linkages. |
| `DoctorProfile` | Clinician profile with medical license number and medical specialty. |
| `HealthCondition` | Allergies, chronic conditions, and past history (`PATIENT_DECLARED` vs `DOCTOR_VERIFIED`). |
| `ClinicalEncounter` | Append-only, immutable consultation records and clinical notes. |
| `Prescription` | Pharmacy-accessible medication orders with status and validity window. |
| `AccessGrant` | Time-bounded 6-digit access tokens granting temporary clinician access. |

---

## Getting Started

### Prerequisites
- **Java 25 LTS**
- **PostgreSQL 15+** running locally on port `5432`

### Database Setup
Create the local PostgreSQL database:
```sql
CREATE DATABASE apollo_db;
```

### Configuration
Configure database credentials in `src/main/resources/application.properties` or set environment variables:
```bash
export DB_USERNAME=postgres
export DB_PASSWORD=your_password
```

### Running the Application
Run using the Maven wrapper:
```bash
# Linux / macOS
./mvnw spring-boot:run

# Windows PowerShell / CMD
.\mvnw.cmd spring-boot:run
```

### API Documentation & Swagger UI
Once running, explore and test the interactive OpenAPI documentation:
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON Spec: `http://localhost:8080/v3/api-docs`

package com.apollo.service.impl;

import com.apollo.domain.entity.DoctorProfile;
import com.apollo.domain.entity.PatientProfile;
import com.apollo.domain.entity.User;
import com.apollo.domain.enums.Role;
import com.apollo.dto.auth.AuthResponse;
import com.apollo.dto.auth.CurrentUserResponse;
import com.apollo.dto.auth.LoginRequest;
import com.apollo.dto.auth.RegisterDoctorRequest;
import com.apollo.dto.auth.RegisterPatientRequest;
import com.apollo.exception.EmailAlreadyExistsException;
import com.apollo.exception.LicenseAlreadyExistsException;
import com.apollo.exception.ResourceNotFoundException;
import com.apollo.repository.DoctorProfileRepository;
import com.apollo.repository.PatientProfileRepository;
import com.apollo.repository.UserRepository;
import com.apollo.security.JwtTokenProvider;
import com.apollo.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    @Override
    @Transactional
    public AuthResponse registerPatient(RegisterPatientRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.ROLE_PATIENT)
                .build();
        user = userRepository.save(user);

        PatientProfile patient = PatientProfile.builder()
                .user(user)
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .dateOfBirth(request.getDateOfBirth())
                .bloodType(request.getBloodType() != null ? request.getBloodType().trim() : null)
                .build();
        patient = patientProfileRepository.save(patient);

        String token = jwtTokenProvider.generateToken(user.getId(), patient.getId(), user.getEmail(), user.getRole());

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .profileId(patient.getId())
                .role(user.getRole())
                .email(user.getEmail())
                .fullName(patient.getFirstName() + " " + patient.getLastName())
                .build();
    }

    @Override
    @Transactional
    public AuthResponse registerDoctor(RegisterDoctorRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        String normalizedLicense = request.getLicenseNumber().trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }

        if (doctorProfileRepository.existsByLicenseNumber(normalizedLicense)) {
            throw new LicenseAlreadyExistsException(normalizedLicense);
        }

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.ROLE_DOCTOR)
                .build();
        user = userRepository.save(user);

        com.apollo.domain.enums.DoctorRole doctorRole = request.getDoctorRole() != null
                ? request.getDoctorRole()
                : com.apollo.domain.enums.DoctorRole.GENERAL_PRACTITIONER;

        DoctorProfile doctor = DoctorProfile.builder()
                .user(user)
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .licenseNumber(normalizedLicense)
                .specialty(request.getSpecialty().trim())
                .doctorRole(doctorRole)
                .build();
        doctor = doctorProfileRepository.save(doctor);

        String token = jwtTokenProvider.generateToken(user.getId(), doctor.getId(), user.getEmail(), user.getRole());

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .profileId(doctor.getId())
                .role(user.getRole())
                .email(user.getEmail())
                .fullName("Dr. " + doctor.getFirstName() + " " + doctor.getLastName())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
            );
        } catch (BadCredentialsException ex) {
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        UUID profileId = null;
        String fullName = null;

        if (user.getRole() == Role.ROLE_PATIENT) {
            PatientProfile p = patientProfileRepository.findByUserId(user.getId()).orElse(null);
            if (p != null) {
                profileId = p.getId();
                fullName = p.getFirstName() + " " + p.getLastName();
            }
        } else if (user.getRole() == Role.ROLE_DOCTOR) {
            DoctorProfile d = doctorProfileRepository.findByUserId(user.getId()).orElse(null);
            if (d != null) {
                profileId = d.getId();
                fullName = "Dr. " + d.getFirstName() + " " + d.getLastName();
            }
        }

        String token = jwtTokenProvider.generateToken(user.getId(), profileId, user.getEmail(), user.getRole());

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .profileId(profileId)
                .role(user.getRole())
                .email(user.getEmail())
                .fullName(fullName)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        UUID profileId = null;
        String fullName = null;

        if (user.getRole() == Role.ROLE_PATIENT) {
            PatientProfile p = patientProfileRepository.findByUserId(user.getId()).orElse(null);
            if (p != null) {
                profileId = p.getId();
                fullName = p.getFirstName() + " " + p.getLastName();
            }
        } else if (user.getRole() == Role.ROLE_DOCTOR) {
            DoctorProfile d = doctorProfileRepository.findByUserId(user.getId()).orElse(null);
            if (d != null) {
                profileId = d.getId();
                fullName = "Dr. " + d.getFirstName() + " " + d.getLastName();
            }
        }

        return CurrentUserResponse.builder()
                .userId(user.getId())
                .profileId(profileId)
                .email(user.getEmail())
                .role(user.getRole())
                .fullName(fullName)
                .createdAt(user.getCreatedAt())
                .build();
    }
}

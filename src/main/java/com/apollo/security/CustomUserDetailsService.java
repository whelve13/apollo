package com.apollo.security;

import com.apollo.domain.entity.DoctorProfile;
import com.apollo.domain.entity.PatientProfile;
import com.apollo.domain.entity.User;
import com.apollo.domain.enums.Role;
import com.apollo.repository.DoctorProfileRepository;
import com.apollo.repository.PatientProfileRepository;
import com.apollo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final DoctorProfileRepository doctorProfileRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        UUID profileId = null;
        if (user.getRole() == Role.ROLE_PATIENT) {
            profileId = patientProfileRepository.findByUserId(user.getId())
                    .map(PatientProfile::getId)
                    .orElse(null);
        } else if (user.getRole() == Role.ROLE_DOCTOR) {
            profileId = doctorProfileRepository.findByUserId(user.getId())
                    .map(DoctorProfile::getId)
                    .orElse(null);
        }

        return CustomUserDetails.create(
                user.getId(),
                profileId,
                user.getEmail(),
                user.getPasswordHash(),
                user.getRole()
        );
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserById(UUID userId) throws UsernameNotFoundException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));

        UUID profileId = null;
        if (user.getRole() == Role.ROLE_PATIENT) {
            profileId = patientProfileRepository.findByUserId(user.getId())
                    .map(PatientProfile::getId)
                    .orElse(null);
        } else if (user.getRole() == Role.ROLE_DOCTOR) {
            profileId = doctorProfileRepository.findByUserId(user.getId())
                    .map(DoctorProfile::getId)
                    .orElse(null);
        }

        return CustomUserDetails.create(
                user.getId(),
                profileId,
                user.getEmail(),
                user.getPasswordHash(),
                user.getRole()
        );
    }
}

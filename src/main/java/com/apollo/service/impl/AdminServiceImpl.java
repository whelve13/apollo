package com.apollo.service.impl;

import com.apollo.domain.entity.DoctorProfile;
import com.apollo.dto.admin.DoctorRoleUpdateResponse;
import com.apollo.dto.admin.UpdateDoctorRoleRequest;
import com.apollo.exception.ResourceNotFoundException;
import com.apollo.repository.DoctorProfileRepository;
import com.apollo.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final DoctorProfileRepository doctorProfileRepository;

    @Override
    @Transactional
    public DoctorRoleUpdateResponse updateDoctorRole(UUID doctorId, UpdateDoctorRoleRequest request) {
        DoctorProfile doctor = doctorProfileRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found: " + doctorId));

        doctor.setDoctorRole(request.getDoctorRole());
        DoctorProfile saved = doctorProfileRepository.save(doctor);
        log.info("Doctor permission role updated: doctorId={}, newRole={}", saved.getId(), saved.getDoctorRole());

        String docName = "Dr. " + saved.getFirstName() + " " + saved.getLastName();

        return DoctorRoleUpdateResponse.builder()
                .doctorId(saved.getId())
                .doctorName(docName)
                .doctorRole(saved.getDoctorRole())
                .message("Doctor permission role successfully updated to " + saved.getDoctorRole())
                .build();
    }
}

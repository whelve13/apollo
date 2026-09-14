package com.apollo.service;

import com.apollo.dto.admin.DoctorRoleUpdateResponse;
import com.apollo.dto.admin.UpdateDoctorRoleRequest;

import java.util.UUID;

public interface AdminService {

    DoctorRoleUpdateResponse updateDoctorRole(UUID doctorId, UpdateDoctorRoleRequest request);
}

package com.apollo.dto.lab;

import com.apollo.domain.enums.LabResultStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A saved lab test panel with its records, in request order")
public class LabTestPanelResponse {

    @Schema(description = "Server-generated panel UUID, repeated on every record of the panel")
    private UUID panelId;

    @Schema(description = "Patient profile UUID")
    private UUID patientId;

    private Instant recordedAt;

    private LabResultStatus status;

    private String templateCode;

    private Integer templateVersion;

    private List<LabTestResultResponse> entries;
}

package com.apollo.dto.lab;

import com.apollo.domain.enums.LabResultStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A lab test panel saved atomically: all entries are stored, or none")
public class LabTestPanelRequest {

    @NotNull(message = "Recorded date and time is required")
    @PastOrPresent(message = "Recorded date cannot be in the future")
    @Schema(description = "Timestamp when the specimen was drawn or test was executed")
    private Instant recordedAt;

    @Schema(description = "DRAFT results are visible only to the doctor who wrote them. Defaults to FINAL.", defaultValue = "FINAL")
    private LabResultStatus status;

    @Size(max = 100, message = "Template code must not exceed 100 characters")
    @Schema(description = "Code of the template the panel was entered from", example = "cbc")
    private String templateCode;

    @Schema(description = "Version of the template the panel was entered from", example = "1")
    private Integer templateVersion;

    @NotEmpty(message = "At least one entry is required")
    @Size(max = 200, message = "A panel may contain at most 200 entries")
    @Valid
    private List<LabTestEntryRequest> entries;
}

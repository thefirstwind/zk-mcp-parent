package com.pajk.mcpmetainfo.core.model.wizard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrichmentRequest {
    @JsonProperty("projectName")
    private String projectName;
    @JsonProperty("originalDescription")
    private String originalDescription;
    @JsonProperty("gitMetadata")
    private GitProjectMetadata gitMetadata;
    @JsonProperty("selectedInterfaces")
    private List<DubboInterfaceInfo> selectedInterfaces;
}

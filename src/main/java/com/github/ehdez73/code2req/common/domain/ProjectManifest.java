package com.github.ehdez73.code2req.common.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ProjectManifest(
    @JsonProperty("targets") List<ScanTarget> targets
) {
}

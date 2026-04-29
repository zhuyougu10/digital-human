package com.medical.common.security.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "security.internal-api")
public class InternalApiAuthProperties {

    @NotBlank(message = "security.internal-api.secret must be configured")
    private String secret;
}

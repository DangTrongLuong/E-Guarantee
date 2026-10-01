package com.example.ecommerce.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@Valid
@ConfigurationProperties(prefix = "cloudinary")
public class CloudinaryProperties {
    @NotBlank(message = "Vui lòng cung cấp thông tin cloudName !")
    private String cloudName;

    @NotBlank(message = "Vui lòng cung cấp thông tin apiKey !")
    private String apiKey;

    @NotBlank(message = "Vui lòng cung cấp thông tin apiSecret !")
    private String apiSecret;

}

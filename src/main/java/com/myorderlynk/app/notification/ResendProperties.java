package com.myorderlynk.app.notification;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "resend")
public class ResendProperties {

    private String apiKey;
    private String apiBaseUrl;
    private String fromEmail;
    private String fromName;
}
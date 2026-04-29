package com.smartvoyage.messaging.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.twilio")
@Getter
@Setter
public class TwilioProperties {
    private boolean enabled;
    private String accountSid;
    private String authToken;
    private String fromNumber;
    private String whatsappFrom;
}

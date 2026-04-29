package com.smartvoyage.messaging.service;

import com.smartvoyage.messaging.config.TwilioProperties;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TwilioService {
    private final TwilioProperties properties;

    @PostConstruct
    void init() {
        if (properties.isEnabled()) {
            Twilio.init(properties.getAccountSid(), properties.getAuthToken());
        }
    }

    public void sendSms(String to, String body) {
        if (!properties.isEnabled() || to == null || to.isBlank()) return;
        Message.creator(new PhoneNumber(to), new PhoneNumber(properties.getFromNumber()), body).create();
    }

    public void sendWhatsApp(String to, String body) {
        if (!properties.isEnabled() || to == null || to.isBlank()) return;
        Message.creator(
                new PhoneNumber("whatsapp:" + to.replace("whatsapp:", "")),
                new PhoneNumber(properties.getWhatsappFrom()),
                body).create();
    }
}

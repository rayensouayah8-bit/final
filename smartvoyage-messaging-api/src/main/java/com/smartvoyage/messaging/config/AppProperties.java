package com.smartvoyage.messaging.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {
    private Upload upload = new Upload();
    private AntiSpam antiSpam = new AntiSpam();
    private Notification notification = new Notification();

    @Getter @Setter
    public static class Upload {
        private String baseDir = "uploads";
        private String audioDir = "uploads/audio";
    }

    @Getter @Setter
    public static class AntiSpam {
        private int maxMessages = 5;
        private int windowSeconds = 10;
        private int duplicateWindowSeconds = 20;
        private List<String> badWords = new ArrayList<>();
    }

    @Getter @Setter
    public static class Notification {
        private long batchDelayMs = 3000;
    }
}

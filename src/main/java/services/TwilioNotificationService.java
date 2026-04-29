package services;

import utils.AppConfig;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Envoi SMS via Twilio REST API (sans SDK).
 */
public final class TwilioNotificationService {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public boolean isConfigured() {
        return !AppConfig.getTwilioAccountSid().isBlank()
                && !AppConfig.getTwilioAuthToken().isBlank()
                && !AppConfig.getTwilioFromPhone().isBlank();
    }

    public void sendFollowUpDoneSms(String toPhone, String followUpTitle) {
        if (!isConfigured()) {
            System.err.println("Twilio SMS skipped: missing config (account_sid/auth_token/from_phone).");
            return;
        }
        String normalizedTo = normalizePhone(AppConfig.getTwilioToPhone());
        if (normalizedTo == null) {
            normalizedTo = normalizePhone(toPhone);
        }
        if (normalizedTo == null) {
            normalizedTo = normalizePhone(AppConfig.getTwilioDefaultToPhone());
        }
        if (normalizedTo == null) {
            return;
        }
        String body = "SmartVoyage: your follow-up \""
                + (followUpTitle == null || followUpTitle.isBlank() ? "task" : followUpTitle)
                + "\" is marked DONE.";
        sendSms(normalizedTo, body);
    }

    public void sendSms(String toPhone, String body) {
        String sid = AppConfig.getTwilioAccountSid();
        String token = AppConfig.getTwilioAuthToken();
        String from = normalizePhone(AppConfig.getTwilioFromPhone());
        if (sid.isBlank() || token.isBlank() || from.isBlank()) {
            System.err.println("Twilio SMS skipped: missing sid/token/from.");
            return;
        }
        String normalizedTo = normalizePhone(toPhone);
        if (normalizedTo == null || body == null || body.isBlank()) {
            System.err.println("Twilio SMS skipped: invalid destination or empty body.");
            return;
        }
        try {
            String form = "To=" + enc(normalizedTo)
                    + "&From=" + enc(from)
                    + "&Body=" + enc(body);
            String basic = Base64.getEncoder().encodeToString((sid + ":" + token).getBytes(StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + sid + "/Messages.json"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                System.err.println("Twilio SMS failed HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 220));
            } else {
                System.out.println("Twilio SMS sent to " + normalizedTo + " (HTTP " + resp.statusCode() + ").");
            }
        } catch (Exception e) {
            System.err.println("Twilio SMS error: " + e.getMessage());
        }
    }

    private static String normalizePhone(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("+")) {
            return trimmed;
        }
        return "+" + trimmed.replaceAll("[^0-9]", "");
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}

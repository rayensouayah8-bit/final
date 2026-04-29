package services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import utils.AppConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * Envoi d’images vers Cloudinary (preset non signé).
 */
public final class CloudinaryService {

    private static final Duration TIMEOUT = Duration.ofSeconds(90);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public CompletableFuture<String> uploadImageBytesAsync(byte[] imageBytes, String contentType, String baseName) {
        return CompletableFuture.supplyAsync(() -> {
            if (!AppConfig.isCloudinaryConfigured()) {
                throw new IllegalStateException(
                        "Cloudinary n’est pas configuré. Ajoutez cloudinary.cloud_name et cloudinary.upload_preset "
                                + "dans config.properties (preset « unsigned » dans le tableau Cloudinary).");
            }
            String cloud = AppConfig.getCloudinaryCloudName();
            String preset = AppConfig.getCloudinaryUploadPreset();
            String url = "https://api.cloudinary.com/v1_1/" + cloud + "/image/upload";
            String boundary = "----SmartVoyageForm" + UUID.randomUUID();
            String mime = contentType != null && !contentType.isBlank() ? contentType : "image/png";
            String fname = (baseName != null && !baseName.isBlank() ? baseName : "hf-gen") + pickExt(mime);

            final byte[] body;
            try {
                body = buildMultipart(boundary, preset, fname, mime, imageBytes);
            } catch (IOException ex) {
                throw new IllegalStateException("Préparation de l’envoi Cloudinary impossible.", ex);
            }

            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(TIMEOUT)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() / 100 != 2) {
                    throw new IllegalStateException("Cloudinary HTTP " + resp.statusCode() + " : " + truncate(resp.body(), 200));
                }
                JsonNode root = JSON.readTree(resp.body());
                String secure = root.path("secure_url").asText(null);
                if (secure == null || secure.isBlank()) {
                    throw new IllegalStateException("Réponse Cloudinary sans secure_url.");
                }
                return secure;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Envoi Cloudinary interrompu.", e);
            } catch (Exception e) {
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new IllegalStateException("Échec upload Cloudinary : " + e.getMessage(), e);
            }
        }, ForkJoinPool.commonPool());
    }

    private static String pickExt(String mime) {
        if (mime.contains("jpeg") || mime.contains("jpg")) {
            return ".jpg";
        }
        if (mime.contains("webp")) {
            return ".webp";
        }
        return ".png";
    }

    private static byte[] buildMultipart(String boundary, String uploadPreset, String filename, String mime,
                                         byte[] fileBytes) throws IOException {
        String dash = "--";
        String crlf = "\r\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream(fileBytes.length + 512);

        out.write((dash + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"upload_preset\"" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
        out.write((uploadPreset + crlf).getBytes(StandardCharsets.UTF_8));

        out.write((dash + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"" + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + mime + crlf + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(fileBytes);
        out.write(crlf.getBytes(StandardCharsets.UTF_8));

        out.write((dash + boundary + dash + crlf).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}

package services.speech;

import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reconnaissance vocale locale offline via Vosk.
 */
public class LocalSpeechFallbackService {

    private static final Pattern TEXT_JSON = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern PARTIAL_JSON = Pattern.compile("\"partial\"\\s*:\\s*\"([^\"]*)\"");
    private static final String[] COMMON_MODEL_DIRS = {
            "Downloads/vosk-model-small-fr-0.22",
            "Downloads/vosk-model-fr-0.22",
            "vosk-model-small-fr-0.22",
            "vosk-model-fr-0.22"
    };
    private static final double SILENCE_THRESHOLD = 180.0;

    public record OfflineTranscription(Optional<String> text, String errorMessage) {}

    public Optional<String> transcribeOffline(Path wavPath) {
        return transcribeOfflineDetailed(wavPath).text();
    }

    public OfflineTranscription transcribeOfflineDetailed(Path wavPath) {
        if (wavPath == null) {
            return new OfflineTranscription(Optional.empty(), "Fichier audio invalide.");
        }
        try {
            Path modelPath = resolveModelPath();
            if (modelPath == null) {
                return new OfflineTranscription(Optional.empty(), "Modèle vocal introuvable. Vérifiez VOSK_MODEL_PATH.");
            }
            if (!isLikelyVoskModelDir(modelPath)) {
                return new OfflineTranscription(Optional.empty(),
                        "Le dossier Vosk ne contient pas les fichiers modèle (conf/am/graph/ivector).");
            }
            try (Model model = new Model(modelPath.toString());
                 AudioInputStream ais = AudioSystem.getAudioInputStream(wavPath.toFile());
                 Recognizer recognizer = new Recognizer(model, ais.getFormat().getSampleRate())) {
                byte[] buffer = new byte[4096];
                int nread;
                String lastPartial = "";
                double peak = 0.0;
                while ((nread = ais.read(buffer)) >= 0) {
                    peak = Math.max(peak, computePeak(buffer, nread));
                    if (recognizer.acceptWaveForm(buffer, nread)) {
                        // on ignore les segments intermédiaires validés pour éviter les répétitions.
                    } else {
                        String partial = extractPartial(recognizer.getPartialResult());
                        if (!partial.isBlank()) {
                            lastPartial = partial;
                        }
                    }
                }
                String finalText = extractText(recognizer.getFinalResult());
                String text = !finalText.isBlank() ? finalText : lastPartial;
                text = normalizeRecognizedText(text);
                if (!text.isBlank()) {
                    return new OfflineTranscription(Optional.of(text), null);
                }
                if (peak < SILENCE_THRESHOLD) {
                    return new OfflineTranscription(Optional.empty(), "Micro détecté mais aucun son (vérifiez le micro actif).");
                }
                return new OfflineTranscription(Optional.empty(), "Parole non reconnue. Parlez plus près du micro.");
            }
        } catch (Exception e) {
            return new OfflineTranscription(Optional.empty(), "Erreur reconnaissance locale: " + e.getMessage());
        }
    }

    private static String extractText(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        Matcher m = TEXT_JSON.matcher(json);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }

    private static String extractPartial(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        Matcher m = PARTIAL_JSON.matcher(json);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }

    private static Path resolveModelPath() {
        String env = System.getenv("VOSK_MODEL_PATH");
        if (env != null && !env.isBlank()) {
            Path p = Path.of(env.trim());
            if (Files.isDirectory(p)) {
                Path normalized = normalizeModelRoot(p);
                if (normalized != null) {
                    return normalized;
                }
            }
        }
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            for (String dir : COMMON_MODEL_DIRS) {
                Path p = Path.of(home, dir.split("/"));
                if (Files.isDirectory(p)) {
                    Path normalized = normalizeModelRoot(p);
                    if (normalized != null) {
                        return normalized;
                    }
                }
            }
        }
        return null;
    }

    private static Path normalizeModelRoot(Path path) {
        if (path == null || !Files.isDirectory(path)) {
            return null;
        }
        if (isLikelyVoskModelDir(path)) {
            return path;
        }
        try (var stream = Files.list(path)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(LocalSpeechFallbackService::isLikelyVoskModelDir)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isLikelyVoskModelDir(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return false;
        }
        return Files.isDirectory(dir.resolve("am"))
                && Files.isDirectory(dir.resolve("graph"))
                && Files.isDirectory(dir.resolve("conf"));
    }

    private static double computePeak(byte[] bytes, int len) {
        if (bytes == null || len < 2) {
            return 0.0;
        }
        double peak = 0.0;
        for (int i = 0; i + 1 < len; i += 2) {
            int lo = bytes[i] & 0xFF;
            int hi = bytes[i + 1];
            short sample = (short) ((hi << 8) | lo);
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak;
    }

    private static String normalizeRecognizedText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] parts = text.trim().replaceAll("\\s+", " ").split(" ");
        List<String> out = new ArrayList<>();
        String prev = null;
        int repeatCount = 0;
        for (String part : parts) {
            String cur = part == null ? "" : part.trim();
            if (cur.isBlank()) {
                continue;
            }
            if (prev != null && prev.equalsIgnoreCase(cur)) {
                repeatCount++;
                // on garde max 2 répétitions consécutives, pas plus
                if (repeatCount >= 2) {
                    continue;
                }
            } else {
                repeatCount = 0;
            }
            out.add(cur);
            prev = cur;
        }
        return String.join(" ", out).trim();
    }
}


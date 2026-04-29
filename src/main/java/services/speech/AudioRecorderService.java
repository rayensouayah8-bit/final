package services.speech;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Locale;

/**
 * Enregistre un échantillon micro au format WAV via Java Sound API.
 */
public class AudioRecorderService {

    public record RecordingResult(File wavFile, String mimeType) {}

    public RecordingResult recordToTempWav(Duration maxDuration) throws IOException {
        Duration safeDuration = (maxDuration == null || maxDuration.isNegative() || maxDuration.isZero())
                ? Duration.ofSeconds(3)
                : maxDuration;
        AudioFormat format = new AudioFormat(16_000.0f, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) {
            throw new IllegalStateException("Micro non trouvé ou format audio non supporté.");
        }
        File tmp = File.createTempFile("smartvoyage-voice-", ".wav");
        tmp.deleteOnExit();
        try (TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info)) {
            line.open(format);
            line.start();
            Thread stopper = new Thread(() -> {
                try {
                    Thread.sleep(safeDuration.toMillis());
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    line.stop();
                    line.close();
                }
            }, "voice-recorder-stopper");
            stopper.setDaemon(true);
            stopper.start();
            try (AudioInputStream stream = new AudioInputStream(line)) {
                AudioSystem.write(stream, AudioFileFormat.Type.WAVE, tmp);
            }
        } catch (LineUnavailableException e) {
            throw new IllegalStateException("Impossible d'accéder au microphone.", e);
        }
        return new RecordingResult(tmp, "audio/wav");
    }
}


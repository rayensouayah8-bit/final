package services.speech;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Transcription Google Cloud Speech-to-Text (synchrone).
 */
public class GoogleSpeechService {

    public Optional<String> transcribe(Path wavPath) {
        if (wavPath == null || !Files.exists(wavPath)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(wavPath);
            ByteString audioBytes = ByteString.copyFrom(bytes);

            RecognitionConfig config = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setSampleRateHertz(16000)
                    .setLanguageCode("fr-FR")
                    .addAlternativeLanguageCodes("en-US")
                    .setEnableAutomaticPunctuation(true)
                    .build();
            RecognitionAudio audio = RecognitionAudio.newBuilder().setContent(audioBytes).build();

            try (SpeechClient speech = createSpeechClient()) {
                RecognizeResponse response = speech.recognize(config, audio);
                for (SpeechRecognitionResult result : response.getResultsList()) {
                    if (result.getAlternativesCount() > 0) {
                        String transcript = result.getAlternatives(0).getTranscript();
                        if (transcript != null && !transcript.isBlank()) {
                            return Optional.of(transcript.trim());
                        }
                    }
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private SpeechClient createSpeechClient() throws IOException {
        String credsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (credsPath != null && !credsPath.isBlank()) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(credsPath))
                    .createScoped("https://www.googleapis.com/auth/cloud-platform");
            SpeechSettings settings = SpeechSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .build();
            return SpeechClient.create(settings);
        }
        return SpeechClient.create();
    }
}


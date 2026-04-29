package services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import utils.AppConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Appels asynchrones à l’API Inference Hugging Face (BLIP, Stable Diffusion).
 * Cache léger + espacement des requêtes pour limiter le rate limiting.
 */
public final class HuggingFaceService {

    public static final String BLIP_CAPTION_URL =
            "https://api-inference.huggingface.co/models/Salesforce/blip-image-captioning-base";
    public static final String STABLE_DIFFUSION_URL =
            "https://api-inference.huggingface.co/models/runwayml/stable-diffusion-v1-5";
    public static final String STABLE_DIFFUSION_FALLBACK_URL =
            "https://api-inference.huggingface.co/models/stabilityai/stable-diffusion-2-1";
    public static final String STABLE_DIFFUSION_XL_URL =
            "https://api-inference.huggingface.co/models/stabilityai/stable-diffusion-xl-base-1.0";
    public static final String STABLE_DIFFUSION_V14_URL =
            "https://api-inference.huggingface.co/models/CompVis/stable-diffusion-v1-4";
    public static final String STABLE_DIFFUSION_ROUTER_URL =
            "https://router.huggingface.co/hf-inference/models/runwayml/stable-diffusion-v1-5";
    public static final String STABLE_DIFFUSION_ROUTER_FALLBACK_URL =
            "https://router.huggingface.co/hf-inference/models/stabilityai/stable-diffusion-2-1";
    public static final String STABLE_DIFFUSION_ROUTER_XL_URL =
            "https://router.huggingface.co/hf-inference/models/stabilityai/stable-diffusion-xl-base-1.0";
    public static final String STABLE_DIFFUSION_ROUTER_V14_URL =
            "https://router.huggingface.co/hf-inference/models/CompVis/stable-diffusion-v1-4";
    public static final String FLUX_SCHNELL_ROUTER_URL =
            "https://router.huggingface.co/hf-inference/models/black-forest-labs/FLUX.1-schnell";
    public static final String FLUX_SCHNELL_API_URL =
            "https://api-inference.huggingface.co/models/black-forest-labs/FLUX.1-schnell";
    public static final String PIPELINE_SD15_URL =
            "https://api-inference.huggingface.co/pipeline/text-to-image/runwayml/stable-diffusion-v1-5";
    public static final String PIPELINE_SD21_URL =
            "https://api-inference.huggingface.co/pipeline/text-to-image/stabilityai/stable-diffusion-2-1";
    public static final String PIPELINE_FLUX_URL =
            "https://api-inference.huggingface.co/pipeline/text-to-image/black-forest-labs/FLUX.1-schnell";
    public static final String CHAT_ROUTER_FLAN_BASE =
            "https://router.huggingface.co/hf-inference/models/google/flan-t5-base";
    public static final String CHAT_API_FLAN_BASE =
            "https://api-inference.huggingface.co/models/google/flan-t5-base";
    public static final String CHAT_ROUTER_FLAN_LARGE =
            "https://router.huggingface.co/hf-inference/models/google/flan-t5-large";
    public static final String CHAT_API_FLAN_LARGE =
            "https://api-inference.huggingface.co/models/google/flan-t5-large";

    private static final Duration TIMEOUT_BLIP = Duration.ofSeconds(60);
    private static final Duration TIMEOUT_SD = Duration.ofSeconds(120);
    private static final long MIN_INTERVAL_MS = 1_200L;
    private static final int MAX_CACHE_ENTRIES = 40;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient httpBlip = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final HttpClient httpSd = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    private final ReentrantLock rateLock = new ReentrantLock();
    private volatile long lastRequestAt;

    private final Map<String, String> captionCache = new ConcurrentHashMap<>();
    private final Map<String, byte[]> imageBytesCache = new ConcurrentHashMap<>();
    private final Map<String, String> imageUrlCache = new ConcurrentHashMap<>();
    private final Map<String, String> textCache = new ConcurrentHashMap<>();

    public record GeneratedPostDraft(String country, String title, String content) {}
    public record NavigationSuggestion(String country, String query) {}

    private void throttle() throws InterruptedException {
        rateLock.lock();
        try {
            long now = System.currentTimeMillis();
            long wait = MIN_INTERVAL_MS - (now - lastRequestAt);
            if (wait > 0) {
                Thread.sleep(wait);
            }
            lastRequestAt = System.currentTimeMillis();
        } finally {
            rateLock.unlock();
        }
    }

    private static void trimCache(Map<?, ?> map) {
        if (map.size() > MAX_CACHE_ENTRIES) {
            map.clear();
        }
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(data);
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            return String.valueOf(data.length);
        }
    }

    /**
     * BLIP : image → légende (remplit typiquement le contenu du post).
     */
    public CompletableFuture<String> captionImageAsync(byte[] imageBytes, String contentType) {
        return CompletableFuture.supplyAsync(() -> {
            if (!AppConfig.hasHuggingfaceInferenceToken()) {
                throw new IllegalStateException(
                        "Token Hugging Face manquant. Ajoutez huggingface.api.token dans config.properties "
                                + "ou la variable d’environnement HUGGINGFACE_API_TOKEN.");
            }
            if (imageBytes == null || imageBytes.length < 32) {
                throw new IllegalStateException("Image trop petite ou invalide.");
            }
            String key = sha256(imageBytes);
            String cached = captionCache.get(key);
            if (cached != null) {
                return cached;
            }
            try {
                throttle();
                String token = AppConfig.getHuggingfaceApiToken();
                String mime = contentType != null && !contentType.isBlank() ? contentType : "application/octet-stream";
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(BLIP_CAPTION_URL))
                        .timeout(TIMEOUT_BLIP)
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", mime)
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(imageBytes))
                        .build();
                HttpResponse<String> resp = sendWithBlipRetry(req);
                String body = resp.body();
                if (resp.statusCode() / 100 != 2) {
                    throw new IllegalStateException(translateHfError(resp.statusCode(), body));
                }
                String caption = parseBlipCaption(body);
                trimCache(captionCache);
                captionCache.put(key, caption);
                return caption;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Génération interrompue.", e);
            } catch (Exception e) {
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new IllegalStateException("BLIP : " + e.getMessage(), e);
            }
        }, ForkJoinPool.commonPool());
    }

    /**
     * Stable Diffusion : texte → image (PNG/JPEG binaire).
     */
    public CompletableFuture<byte[]> textToImageBytesAsync(String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            if (!AppConfig.hasHuggingfaceInferenceToken()) {
                throw new IllegalStateException(
                        "Token Hugging Face manquant. Ajoutez huggingface.api.token dans config.properties.");
            }
            String p = prompt != null ? prompt.trim() : "";
            if (p.length() < 3) {
                throw new IllegalStateException("La description doit contenir au moins quelques mots.");
            }
            if (p.length() > 500) {
                p = p.substring(0, 500);
            }
            String cacheKey = p.toLowerCase(Locale.ROOT);
            byte[] cached = imageBytesCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
            try {
                throttle();
                String token = AppConfig.getHuggingfaceApiToken();
                String jsonBody = json.createObjectNode().put("inputs", p).toString();
                HttpResponse<byte[]> resp = sendStableDiffusionWithFallback(token, jsonBody);
                byte[] data = resp.body();
                if (resp.statusCode() / 100 != 2) {
                    String err = data != null && data.length > 0
                            ? new String(data, 0, Math.min(data.length, 800), StandardCharsets.UTF_8)
                            : "";
                    throw new IllegalStateException(translateHfError(resp.statusCode(), err));
                }
                String ct = resp.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
                if (ct.contains("json")) {
                    throw new IllegalStateException(translateHfError(resp.statusCode(), new String(data, StandardCharsets.UTF_8)));
                }
                trimCache(imageBytesCache);
                imageBytesCache.put(cacheKey, data);
                return data;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Génération interrompue.", e);
            } catch (Exception e) {
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new IllegalStateException("Stable Diffusion : " + e.getMessage(), e);
            }
        }, ForkJoinPool.commonPool());
    }

    /**
     * Texte → image → URL Cloudinary (avec cache par prompt).
     */
    public CompletableFuture<String> textToImageUrlAsync(String prompt, CloudinaryService cloudinary) {
        String p = prompt != null ? prompt.trim().toLowerCase(Locale.ROOT) : "";
        String urlHit = imageUrlCache.get(p);
        if (urlHit != null) {
            return CompletableFuture.completedFuture(urlHit);
        }
        return textToImageBytesAsync(prompt).thenCompose(bytes -> {
            String mime = sniffImageMime(bytes);
            return cloudinary.uploadImageBytesAsync(bytes, mime, "sd-" + System.currentTimeMillis())
                    .thenApply(url -> {
                        trimCache(imageUrlCache);
                        imageUrlCache.put(p, url);
                        return url;
                    });
        });
    }

    public CompletableFuture<GeneratedPostDraft> generatePostFromCountryAsync(String country) {
        return CompletableFuture.supplyAsync(() -> {
            String c = country != null ? country.trim() : "";
            if (c.length() < 2) {
                throw new IllegalStateException("Donnez un pays valide.");
            }
            try {
                if (AppConfig.hasHuggingfaceInferenceToken()) {
                    String prompt = """
                            Tu es un assistant SmartVoyage.
                            Réponds UNIQUEMENT en JSON valide (sans markdown) avec les clés:
                            country, title, content.
                            Langue: français.
                            Contraintes:
                            - title: 10 à 80 caractères
                            - content: 120 à 220 mots, ton inspirant, concret, orienté voyage
                            Pays: %s
                            """.formatted(c);
                    String raw = runTextInference("gen:" + c.toLowerCase(Locale.ROOT), prompt, 340);
                    JsonNode obj = extractJsonObject(raw);
                    if (obj != null) {
                        String outCountry = obj.path("country").asText(c).trim();
                        String title = obj.path("title").asText("").trim();
                        String content = obj.path("content").asText("").trim();
                        if (title.length() >= 6 && content.length() >= 40) {
                            if (title.length() > 100) {
                                title = title.substring(0, 100);
                            }
                            if (content.length() > 5000) {
                                content = content.substring(0, 5000);
                            }
                            return new GeneratedPostDraft(outCountry, title, content);
                        }
                    }
                }
            } catch (Exception ignored) {
                // fallback local ci-dessous
            }
            return localGeneratedPost(c);
        }, ForkJoinPool.commonPool());
    }

    public CompletableFuture<NavigationSuggestion> suggestNavigationAsync(String userText) {
        return CompletableFuture.supplyAsync(() -> {
            String text = userText != null ? userText.trim() : "";
            if (text.length() < 2) {
                throw new IllegalStateException("Entrez une demande de navigation.");
            }
            try {
                if (AppConfig.hasHuggingfaceInferenceToken()) {
                    String prompt = """
                            Tu es un assistant de navigation SmartVoyage.
                            Réponds UNIQUEMENT en JSON valide:
                            {"country":"...","query":"..."}
                            Règles:
                            - country: pays détecté ou "" si absent
                            - query: texte de recherche court (2 à 80 caractères)
                            Entrée utilisateur: %s
                            """.formatted(text);
                    String raw = runTextInference("nav:" + text.toLowerCase(Locale.ROOT), prompt, 120);
                    JsonNode obj = extractJsonObject(raw);
                    if (obj != null) {
                        String country = obj.path("country").asText("").trim();
                        String query = obj.path("query").asText(text).trim();
                        if (query.isBlank()) {
                            query = text;
                        }
                        if (query.length() > 120) {
                            query = query.substring(0, 120);
                        }
                        return new NavigationSuggestion(country, query);
                    }
                }
            } catch (Exception ignored) {
                // fallback local ci-dessous
            }
            return localNavigation(text);
        }, ForkJoinPool.commonPool());
    }

    private static String sniffImageMime(byte[] data) {
        if (data == null || data.length < 4) {
            return "image/png";
        }
        if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8) {
            return "image/jpeg";
        }
        if (data[0] == (byte) 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return "image/png";
        }
        if (data.length > 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F') {
            return "image/webp";
        }
        return "image/png";
    }

    private HttpResponse<String> sendWithBlipRetry(HttpRequest req) throws IOException, InterruptedException {
        HttpResponse<String> resp = httpBlip.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() == 503) {
            Thread.sleep(2_000L);
            resp = httpBlip.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        return resp;
    }

    private HttpResponse<byte[]> sendSdWithRetry(HttpRequest req) throws IOException, InterruptedException {
        HttpResponse<byte[]> resp = httpSd.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() == 503) {
            Thread.sleep(3_000L);
            resp = httpSd.send(req, HttpResponse.BodyHandlers.ofByteArray());
        }
        return resp;
    }

    private HttpResponse<byte[]> sendStableDiffusionWithFallback(String token, String jsonBody)
            throws IOException, InterruptedException {
        String[] urls = {
                FLUX_SCHNELL_ROUTER_URL,
                STABLE_DIFFUSION_ROUTER_URL,
                STABLE_DIFFUSION_ROUTER_FALLBACK_URL,
                STABLE_DIFFUSION_ROUTER_XL_URL,
                STABLE_DIFFUSION_ROUTER_V14_URL,
                FLUX_SCHNELL_API_URL,
                STABLE_DIFFUSION_URL,
                STABLE_DIFFUSION_FALLBACK_URL,
                STABLE_DIFFUSION_XL_URL,
                STABLE_DIFFUSION_V14_URL,
                PIPELINE_FLUX_URL,
                PIPELINE_SD21_URL,
                PIPELINE_SD15_URL
        };
        HttpResponse<byte[]> last = null;
        for (String url : urls) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT_SD)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("Accept", "*/*")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<byte[]> resp = sendSdWithRetry(req);
            if (resp.statusCode() / 100 == 2) {
                return resp;
            }
            String errText = "";
            if (resp.body() != null && resp.body().length > 0) {
                errText = new String(resp.body(), 0, Math.min(resp.body().length, 700), StandardCharsets.UTF_8);
            }
            boolean tryNext = resp.statusCode() == 404
                    || resp.statusCode() == 410
                    || (resp.statusCode() == 400 && errText.toLowerCase(Locale.ROOT).contains("not supported by provider"));
            if (!tryNext) {
                return resp;
            }
            last = resp;
        }
        if (last == null) {
            throw new IOException("Aucune réponse Hugging Face reçue.");
        }
        return last;
    }

    private String parseBlipCaption(String body) throws IOException {
        JsonNode root = json.readTree(body);
        if (root.has("error")) {
            throw new IllegalStateException(root.get("error").asText("Erreur Hugging Face."));
        }
        if (root.isArray() && root.size() > 0) {
            JsonNode first = root.get(0);
            if (first.has("generated_text")) {
                return first.get("generated_text").asText().trim();
            }
        }
        if (root.has("generated_text")) {
            return root.get("generated_text").asText().trim();
        }
        throw new IllegalStateException("Réponse BLIP inattendue (pas de generated_text).");
    }

    private String runTextInference(String cacheKey, String prompt, int maxTokens) {
        String key = cacheKey != null ? cacheKey : prompt;
        String hit = textCache.get(key);
        if (hit != null) {
            return hit;
        }
        try {
            throttle();
            String token = AppConfig.getHuggingfaceApiToken();
            HttpResponse<String> resp = sendTextWithFallback(token, prompt, maxTokens);
            String body = resp.body() != null ? resp.body() : "";
            if (resp.statusCode() / 100 != 2) {
                throw new IllegalStateException(translateHfError(resp.statusCode(), body));
            }
            String text = parseGeneratedTextBody(body);
            trimCache(textCache);
            textCache.put(key, text);
            return text;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Requête chatbot interrompue.", e);
        } catch (Exception e) {
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException("Chatbot HF: " + e.getMessage(), e);
        }
    }

    private HttpResponse<String> sendTextWithFallback(String token, String prompt, int maxTokens)
            throws IOException, InterruptedException {
        String[] urls = {
                CHAT_ROUTER_FLAN_BASE,
                CHAT_API_FLAN_BASE,
                CHAT_ROUTER_FLAN_LARGE,
                CHAT_API_FLAN_LARGE
        };
        HttpResponse<String> last = null;
        String payload = json.createObjectNode()
                .put("inputs", prompt)
                .set("parameters", json.createObjectNode()
                        .put("max_new_tokens", Math.max(64, Math.min(maxTokens, 512)))
                        .put("temperature", 0.55)
                        .put("return_full_text", false))
                .toString();
        for (String url : urls) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = httpBlip.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 503) {
                Thread.sleep(1800L);
                resp = httpBlip.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            }
            if (resp.statusCode() / 100 == 2) {
                return resp;
            }
            String b = resp.body() != null ? resp.body().toLowerCase(Locale.ROOT) : "";
            boolean tryNext = resp.statusCode() == 404 || resp.statusCode() == 410 || resp.statusCode() == 503
                    || (resp.statusCode() == 400 && b.contains("not supported by provider"));
            if (!tryNext) {
                return resp;
            }
            last = resp;
        }
        if (last == null) {
            throw new IOException("Aucune réponse API chatbot.");
        }
        return last;
    }

    private String parseGeneratedTextBody(String body) throws IOException {
        JsonNode root = json.readTree(body);
        if (root.isObject() && root.has("generated_text")) {
            return root.get("generated_text").asText("");
        }
        if (root.isArray() && !root.isEmpty()) {
            JsonNode first = root.get(0);
            if (first.has("generated_text")) {
                return first.get("generated_text").asText("");
            }
        }
        return body;
    }

    private JsonNode extractJsonObject(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }
        String t = rawText.trim();
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        String jsonPart = t.substring(start, end + 1);
        try {
            JsonNode n = json.readTree(jsonPart);
            return n.isObject() ? n : null;
        } catch (Exception e) {
            return null;
        }
    }

    private GeneratedPostDraft localGeneratedPost(String country) {
        String c = country == null ? "ce pays" : country.trim();
        String title = "Escapade inoubliable en " + c;
        String content = ("Partez a la decouverte de " + c + " avec un itineraire pense pour les voyageurs qui aiment " +
                "les experiences authentiques. Commencez par explorer les quartiers les plus vivants, goutez la cuisine locale " +
                "dans des adresses recommandees par les habitants, puis consacrez une journee aux paysages iconiques pour " +
                "capturer de superbes souvenirs. Alternez moments culturels, pauses detente et activites en plein air pour garder " +
                "un rythme agreable. Pensez a reserver quelques experiences a l'avance, notamment les visites populaires et les " +
                "excursions guidees. En fin de sejour, choisissez un lieu panoramique pour conclure votre voyage en beaute et " +
                "partager votre aventure avec la communaute SmartVoyage.")
                .replaceAll("\\s+", " ").trim();
        return new GeneratedPostDraft(c, title.length() > 100 ? title.substring(0, 100) : title, content);
    }

    private NavigationSuggestion localNavigation(String userText) {
        String t = userText == null ? "" : userText.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        String[] countries = {
                "france", "maroc", "tunisie", "algerie", "espagne", "italie", "allemagne",
                "portugal", "canada", "japon", "turquie", "egypte", "usa"
        };
        String country = "";
        for (String c : countries) {
            if (lower.contains(c)) {
                country = Character.toUpperCase(c.charAt(0)) + c.substring(1);
                break;
            }
        }
        String query = t.replaceAll("(?i)\\b(affiche|montre|cherche|posts?|sur|de|du|des|les|la|le)\\b", " ")
                .replaceAll("\\s+", " ").trim();
        if (query.isBlank()) {
            query = t;
        }
        return new NavigationSuggestion(country, query);
    }

    private String translateHfError(int code, String body) {
        String hint = body == null ? "" : body.replace("\n", " ").trim();
        if (hint.length() > 220) {
            hint = hint.substring(0, 220) + "…";
        }
        return switch (code) {
            case 401, 403 -> "Clé Hugging Face refusée (401/403). Vérifiez le token dans config.properties.";
            case 429 -> "Trop de requêtes (429). Patientez une minute avant de réessayer.";
            case 503 -> "Modèle en chargement ou indisponible (503). Réessayez dans quelques instants."
                    + (hint.isEmpty() ? "" : " Détail : " + hint);
            default -> "Erreur API Hugging Face (HTTP " + code + ")." + (hint.isEmpty() ? "" : " " + hint);
        };
    }
}

package services.gestionposts;

import enums.gestionposts.Sentiment;
import models.gestionposts.Comment;
import services.gestionutilisateurs.ReputationService;

import java.sql.SQLException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;

/**
 * Service pour la gestion des commentaires avec validation.
 */
public class CommentService {

    private final CommentDAO commentDAO;
    private final ReputationService reputationService;

    private static final Set<String> POSITIVE_WORDS = Set.of(
            "super", "genial", "magnifique", "parfait", "excellent",
            "amazing", "great", "love", "beautiful", "fantastic"
    );

    private static final Set<String> NEGATIVE_WORDS = Set.of(
            "decu", "horrible", "mauvais", "cher", "probleme",
            "bad", "terrible", "awful", "disappointed", "hate"
    );

    private static final Set<String> BAD_WORDS = Set.of(
            "merde", "putain", "con", "idiot", "stupide",
            "fuck", "shit", "damn", "stupid", "dumb", "bitch",
            "caca", "nul"
    );

    public CommentService() {
        this.commentDAO = new CommentDAOImpl();
        this.reputationService = new ReputationService();
    }

    /**
     * Analyse le sentiment d'un texte de commentaire.
     * Utilise une analyse par mots-cles (francais/anglais).
     */
    public Sentiment analyzeSentiment(String content) {
        if (content == null || content.isBlank()) return Sentiment.NEUTRAL;
        String normalized = normalize(content);
        String[] words = normalized.split("[^\\p{L}\\p{Nd}']+");
        int totalWords = 0;
        int positiveCount = 0;
        int negativeCount = 0;
        for (String w : words) {
            if (w == null || w.isBlank()) {
                continue;
            }
            totalWords++;
            if (POSITIVE_WORDS.contains(w)) {
                positiveCount++;
            }
            if (NEGATIVE_WORDS.contains(w)) {
                negativeCount++;
            }
        }
        if (totalWords == 0) {
            return Sentiment.NEUTRAL;
        }
        double score = (double) (positiveCount - negativeCount) / (double) totalWords;
        if (score > 0.2d) return Sentiment.POSITIVE;
        if (score < -0.2d) return Sentiment.NEGATIVE;
        return Sentiment.NEUTRAL;
    }

    /**
     * Calcule la repartition des sentiments pour une liste de commentaires.
     */
    public Map<Sentiment, Integer> getSentimentDistribution(List<Comment> comments) {
        Map<Sentiment, Integer> distribution = new HashMap<>();
        distribution.put(Sentiment.POSITIVE, 0);
        distribution.put(Sentiment.NEUTRAL, 0);
        distribution.put(Sentiment.NEGATIVE, 0);

        for (Comment comment : comments) {
            Sentiment s = comment.getSentiment() != null ? comment.getSentiment() : analyzeSentiment(comment.getContent());
            distribution.merge(s, 1, Integer::sum);
        }
        return distribution;
    }

    public record ModerationResult(
            String sanitizedContent,
            boolean badwordsDetected,
            List<String> detectedBadwords,
            Sentiment sentiment
    ) {
    }

    public ModerationResult moderate(String rawContent) {
        String input = rawContent != null ? rawContent : "";
        String[] tokens = input.split("(?<=\\b)|(?=\\b)");
        StringBuilder out = new StringBuilder(input.length());
        Set<String> detected = new LinkedHashSet<>();
        for (String t : tokens) {
            String n = normalize(t);
            if (!n.isBlank() && BAD_WORDS.contains(n)) {
                detected.add(n);
                out.append("*".repeat(t.length()));
                System.out.println("Badword détecté: " + n);
            } else {
                out.append(t);
            }
        }
        String sanitized = out.toString();
        Sentiment sentiment = analyzeSentiment(sanitized);
        return new ModerationResult(sanitized, !detected.isEmpty(), new ArrayList<>(detected), sentiment);
    }

    /**
     * Crée un nouveau commentaire avec validation.
     *
     * @param comment Le commentaire à créer
     * @throws IllegalArgumentException Si les données sont invalides
     * @throws SQLException En cas d'erreur base de données
     */
    public void create(Comment comment) throws IllegalArgumentException, SQLException {
        validate(comment);
        ModerationResult r = moderate(comment.getContent());
        comment.setContent(r.sanitizedContent());
        comment.setSentiment(r.sentiment());
        commentDAO.create(comment);
        if (comment.getUserId() != null) {
            reputationService.addPoints(comment.getUserId(), 5, "COMMENT_CREATED", "comment", comment.getId());
            if (r.sentiment() == Sentiment.POSITIVE) {
                reputationService.addPoints(comment.getUserId(), 3, "COMMENT_HIGHLIGHT_POSITIVE", "comment", comment.getId());
            }
        }
    }

    /**
     * Met à jour un commentaire avec validation.
     *
     * @param comment Le commentaire à mettre à jour
     * @throws IllegalArgumentException Si les données sont invalides
     * @throws SQLException En cas d'erreur base de données
     */
    public void update(Comment comment) throws IllegalArgumentException, SQLException {
        validate(comment);
        ModerationResult r = moderate(comment.getContent());
        comment.setContent(r.sanitizedContent());
        comment.setSentiment(r.sentiment());
        commentDAO.update(comment);
    }

    /**
     * Supprime un commentaire.
     *
     * @param id L'ID du commentaire
     * @throws SQLException En cas d'erreur base de données
     */
    public void delete(Long id) throws SQLException {
        commentDAO.delete(id);
    }

    /**
     * Vérifie si l'utilisateur est l'auteur du commentaire.
     *
     * @param commentId L'ID du commentaire
     * @param userId L'ID de l'utilisateur
     * @return true si l'utilisateur est l'auteur
     * @throws SQLException En cas d'erreur base de données
     */
    public boolean isAuthor(Long commentId, Integer userId) throws SQLException {
        return commentDAO.isAuthor(commentId, userId);
    }

    /**
     * Récupère les commentaires d'un post triés par date décroissante.
     *
     * @param postId L'ID du post
     * @return La liste des commentaires
     * @throws SQLException En cas d'erreur base de données
     */
    public List<Comment> findByPostIdOrdered(Long postId) throws SQLException {
        return commentDAO.findByPostIdOrdered(postId);
    }

    /**
     * Compte les commentaires d'un post.
     *
     * @param postId L'ID du post
     * @return Le nombre de commentaires
     * @throws SQLException En cas d'erreur base de données
     */
    public int countByPostId(Long postId) throws SQLException {
        return commentDAO.countByPostId(postId);
    }

    /**
     * Récupère tous les commentaires pour l'admin.
     *
     * @return La liste de tous les commentaires
     * @throws SQLException En cas d'erreur base de données
     */
    public List<Comment> findAllForAdmin() throws SQLException {
        return commentDAO.findAllForAdmin();
    }

    /**
     * Compte le nombre total de commentaires.
     *
     * @return Le nombre total de commentaires
     * @throws SQLException En cas d'erreur base de données
     */
    public int countAll() throws SQLException {
        return commentDAO.countAll();
    }

    /**
     * Valide un commentaire.
     *
     * @param comment Le commentaire à valider
     * @throws IllegalArgumentException Si les données sont invalides
     */
    private void validate(Comment comment) throws IllegalArgumentException {
        if (comment.getContent() == null || comment.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Le contenu du commentaire est obligatoire.");
        }

        String content = comment.getContent().trim();

        if (content.length() < 5) {
            throw new IllegalArgumentException("Le commentaire doit contenir au moins 5 caractères.");
        }

        if (content.length() > 1000) {
            throw new IllegalArgumentException("Le commentaire ne doit pas dépasser 1000 caractères.");
        }

        if (comment.getUserId() == null) {
            throw new IllegalArgumentException("L'utilisateur est obligatoire.");
        }

        if (comment.getPostId() == null) {
            throw new IllegalArgumentException("Le post est obligatoire.");
        }
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String low = s.toLowerCase(Locale.ROOT);
        String nfd = Normalizer.normalize(low, Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{M}+", "").trim();
    }
}

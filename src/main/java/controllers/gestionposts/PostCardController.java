package controllers.gestionposts;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import models.gestionposts.Post;
import models.gestionutilisateurs.User;
import services.gestionposts.LikeService;
import utils.NavigationManager;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Contrôleur pour une carte de post individuelle.
 * Affiche les informations du post et gère les actions.
 */
public class PostCardController implements Initializable {

    @FXML private VBox cardRoot;
    @FXML private ImageView postImage;
    @FXML private Label titleLabel;
    @FXML private Label contentLabel;
    @FXML private Label locationLabel;
    @FXML private Label dateLabel;
    @FXML private HBox statsBox;
    @FXML private Label likesLabel;
    @FXML private Label commentsLabel;
    @FXML private Label trendingLabel;
    @FXML private Button viewButton;
    @FXML private Button likeButton;
    @FXML private Button editButton;
    @FXML private Button deleteButton;

    private Post post;
    private Consumer<Post> onEdit;
    private Consumer<Post> onDelete;
    private Consumer<Post> onView;
    private Runnable onLike;
    private final LikeService likeService;
    /** Si false (vue « tous les posts »), masque toujours Modifier / Supprimer même pour l'auteur. */
    private boolean ownerActionsAllowed;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final String DEFAULT_IMAGE = "https://images.unsplash.com/photo-1469854523086-cc02fe5d8800?w=400&h=300&fit=crop";

    public PostCardController() {
        this.likeService = new LikeService();
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Make the card clickable to open detail view
        setupCardClickHandler();
    }

    private void setupCardClickHandler() {
        if (cardRoot != null) {
            cardRoot.setOnMouseClicked(event -> {
                // Only navigate if not clicking on buttons
                if (event.getTarget() == cardRoot || 
                    event.getTarget() instanceof javafx.scene.image.ImageView ||
                    event.getTarget() instanceof Label) {
                    onView();
                }
            });
            // Change cursor to hand on hover
            cardRoot.setOnMouseEntered(event -> cardRoot.setStyle(cardRoot.getStyle() + "; -fx-cursor: hand;"));
            cardRoot.setOnMouseExited(event -> cardRoot.setStyle(cardRoot.getStyle().replace("; -fx-cursor: hand;", "")));
        }
    }

    public void setPost(Post post) {
        this.post = post;
        updateUI();
    }

    public void setOnEdit(Consumer<Post> onEdit) {
        this.onEdit = onEdit;
    }

    public void setOnDelete(Consumer<Post> onDelete) {
        this.onDelete = onDelete;
    }

    public void setOnView(Consumer<Post> onView) {
        this.onView = onView;
    }

    public void setOnLike(Runnable onLike) {
        this.onLike = onLike;
    }

    /**
     * @param allowed true uniquement en vue « Mes posts » ; la carte n'affiche Modifier/Supprimer que si l'utilisateur est l'auteur.
     */
    public void setOwnerActionsAllowed(boolean allowed) {
        this.ownerActionsAllowed = allowed;
        if (post != null) {
            updateActionButtonsVisibility();
        }
    }

    public void refreshLikeCount() {
        if (post != null) {
            try {
                int count = likeService.countByPostId(post.getId());
                post.setNbLikes(count);
                updateUI();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateUI() {
        if (post == null) return;

        // Titre
        titleLabel.setText(post.getTitre());

        // Contenu (résumé)
        String resume = post.getContenuResume(120);
        contentLabel.setText(resume);

        // Localisation
        locationLabel.setText("📍 " + (post.getLocation() != null ? post.getLocation() : "Non spécifié"));

        // Date
        if (post.getCreatedAt() != null) {
            dateLabel.setText("📅 " + post.getCreatedAt().format(DATE_FORMATTER));
        } else {
            dateLabel.setText("");
        }

        // Image
        loadImage();

        // Statistiques
        int likes = post.getNbLikes() != null ? post.getNbLikes() : 0;
        int comments = post.getNbCommentaires() != null ? post.getNbCommentaires() : 0;
        likesLabel.setText("❤️ " + likes);
        commentsLabel.setText("💬 " + comments);
        if (trendingLabel != null) {
            boolean tr = Boolean.TRUE.equals(post.getTrending());
            trendingLabel.setVisible(tr);
            trendingLabel.setManaged(tr);
            if (tr) {
                double score = post.getTrendingScore() != null ? post.getTrendingScore() : 0.0;
                trendingLabel.setText("🔥 Tendance · score " + String.format("%.1f", score));
                trendingLabel.setStyle("-fx-background-color: rgba(249,115,22,0.2); -fx-text-fill: #fb923c; -fx-padding: 4 8; -fx-background-radius: 12;");
            }
        }

        // Update like button style based on current user
        updateLikeButtonStyle();

        // Show edit/delete buttons only for post author
        updateActionButtonsVisibility();
    }

    private void updateActionButtonsVisibility() {
        Optional<User> currentUser = NavigationManager.getInstance().sessionUser();
        boolean isAuthor = currentUser.isPresent() &&
                          post.getUserId() != null &&
                          currentUser.get().getId().intValue() == post.getUserId();
        boolean show = ownerActionsAllowed && isAuthor;

        if (editButton != null) {
            editButton.setVisible(show);
            editButton.setManaged(show);
        }
        if (deleteButton != null) {
            deleteButton.setVisible(show);
            deleteButton.setManaged(show);
        }
    }

    private void updateLikeButtonStyle() {
        Optional<User> user = NavigationManager.getInstance().sessionUser();
        if (user.isPresent()) {
            try {
                boolean hasLiked = likeService.hasLiked(user.get().getId().intValue(), post.getId());
                if (hasLiked) {
                    likeButton.setText("Aimé");
                } else {
                    likeButton.setText("J'aime");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void loadImage() {
        String imageUrl = post.getImageUrl();
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            imageUrl = DEFAULT_IMAGE;
        } else {
            // Convert local file path to URI if needed
            imageUrl = resolveImageUrl(imageUrl);
        }

        try {
            Image image = new Image(imageUrl, true);
            image.progressProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() >= 1.0) {
                    if (image.isError()) {
                        loadDefaultImage();
                    } else {
                        postImage.setImage(image);
                    }
                }
            });
        } catch (Exception e) {
            loadDefaultImage();
        }
    }

    private String resolveImageUrl(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return DEFAULT_IMAGE;
        }

        String path = imagePath.trim();

        // If it's already a URL (http/https/file), use it as is
        if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("file:")) {
            return path;
        }

        // If it's a Windows path (C:\) or absolute path, convert to file URI
        if (path.startsWith("C:\\") || path.startsWith("/") || path.contains("\\")) {
            try {
                java.io.File file = new java.io.File(path);
                if (file.exists()) {
                    return file.toURI().toString();
                }
            } catch (Exception e) {
                // Fall through to default
            }
        }

        // If it's a classpath resource (starts with /)
        if (path.startsWith("/")) {
            var url = getClass().getResource(path);
            if (url != null) {
                return url.toExternalForm();
            }
        }

        return DEFAULT_IMAGE;
    }

    private void loadDefaultImage() {
        try {
            Image defaultImg = new Image(DEFAULT_IMAGE, 400, 300, true, true, true);
            postImage.setImage(defaultImg);
        } catch (Exception e) {
            // Si même l'image par défaut échoue, laisser vide
        }
    }

    @FXML
    private void onView() {
        System.out.println("[DEBUG] PostCardController.onView() called - post: " + (post != null ? post.getId() + " - " + post.getTitre() : "null"));
        if (post != null) {
            // Navigate to post detail page (not a modal window)
            NavigationManager.getInstance().showPostDetail(post, ownerActionsAllowed);
        }
    }

    @FXML
    private void onLike() {
        if (post == null) return;

        Optional<User> user = NavigationManager.getInstance().sessionUser();
        if (!user.isPresent()) {
            showAlert(javafx.scene.control.Alert.AlertType.WARNING, "Connexion requise", "Vous devez être connecté pour liker un post.");
            return;
        }

        try {
            boolean added = likeService.addLike(user.get().getId().intValue(), post.getId());
            if (added) {
                // Like added
                refreshLikeCount();
                if (onLike != null) {
                    onLike.run();
                }
            } else {
                // Already liked - unlike it
                likeService.removeLike(user.get().getId().intValue(), post.getId());
                refreshLikeCount();
                if (onLike != null) {
                    onLike.run();
                }
            }
        } catch (SQLException e) {
            showAlert(javafx.scene.control.Alert.AlertType.ERROR, "Erreur", "Erreur lors du like: " + e.getMessage());
        }
    }

    @FXML
    private void onEdit() {
        if (onEdit != null && post != null) {
            onEdit.accept(post);
        }
    }

    @FXML
    private void onDelete() {
        if (onDelete != null && post != null) {
            onDelete.accept(post);
        }
    }

    private void showAlert(javafx.scene.control.Alert.AlertType type, String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

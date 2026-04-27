package controllers.gestionoffres;

import controllers.home.SignedInPageControllerBase;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import models.gestionagences.AgencyAccount;
import models.gestionagences.ImageAsset;
import org.controlsfx.control.Notifications;
import services.gestionagences.AgencyAccountService;
import utils.NavigationManager;
import utils.StarfieldHelper;

import java.io.ByteArrayInputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class OffersGuestController extends SignedInPageControllerBase {

    /** Guest preview cards only (no offers-module persistence). */
    private record GuestOffer(Long id, String title, String description, Double price, String destination, Integer createdBy) {}

    private static final String IMG_MALDIVES = "/images/welcome/featured-maldives-beach.jpg";
    private static final String IMG_PARIS = "/images/welcome/featured-paris-eiffel.jpg";
    private static final String IMG_HERO = "/images/welcome/hero-aerial-lagoon.jpg";
    private static final double CARD_W = 360.0;
    private static final double CARD_IMAGE_H = 390.0;
    private static final double CARD_ARC = 44.0;

    @FXML
    private VBox featuredCard1;
    @FXML
    private VBox featuredCard2;
    @FXML
    private VBox featuredCard3;
    @FXML
    private VBox featuredCard4;

    @FXML
    private ImageView offerImage1;
    @FXML
    private ImageView offerImage2;
    @FXML
    private ImageView offerImage3;
    @FXML
    private ImageView offerImage4;
    @FXML
    private ImageView agencyLogo1;
    @FXML
    private ImageView agencyLogo2;
    @FXML
    private ImageView agencyLogo3;
    @FXML
    private ImageView agencyLogo4;

    @FXML
    private Label offerDestination1;
    @FXML
    private Label offerDestination2;
    @FXML
    private Label offerDestination3;
    @FXML
    private Label offerDestination4;
    @FXML
    private Label offerTitle1;
    @FXML
    private Label offerTitle2;
    @FXML
    private Label offerTitle3;
    @FXML
    private Label offerTitle4;
    @FXML
    private Label offerDescription1;
    @FXML
    private Label offerDescription2;
    @FXML
    private Label offerDescription3;
    @FXML
    private Label offerDescription4;
    @FXML
    private Label offerPrice1;
    @FXML
    private Label offerPrice2;
    @FXML
    private Label offerPrice3;
    @FXML
    private Label offerPrice4;
    @FXML
    private Label agencyName1;
    @FXML
    private Label agencyName2;
    @FXML
    private Label agencyName3;
    @FXML
    private Label agencyName4;

    @FXML
    private TextField searchField;
    @FXML
    private HBox offersSearchShell;
    /** Present only on {@code offers_signed_in.fxml}; guest offers page has no starfield. */
    @FXML
    private Pane signedInCosmicStarfieldPane;

    private final AgencyAccountService agencyAccountService = new AgencyAccountService();
    private List<GuestOffer> allOffers = new ArrayList<>();
    private final Map<Integer, AgencyAccount> agencyByResponsable = new HashMap<>();
    private final Map<Long, Image> agencyLogoCache = new HashMap<>();

    @FXML
    private void initialize() {
        initSignedInSidebar();
        if (signedInCosmicStarfieldPane != null) {
            StarfieldHelper.populate(signedInCosmicStarfieldPane);
        }
        if (!hasOffersUi()) {
            return;
        }
        loadOffers();
        loadAgencyAccounts();
        setupCardClipping();
        setupAgencyLogoClipping();
        installSearchShellFocusState();
        renderFeaturedOffers(allOffers);
        if (offerImage3 != null) {
            offerImage3.setEffect(new GaussianBlur(8));
        }
    }

    private boolean hasOffersUi() {
        return featuredCard1 != null
                && featuredCard2 != null
                && featuredCard3 != null
                && featuredCard4 != null
                && offerImage1 != null
                && offerImage2 != null
                && offerImage3 != null
                && offerImage4 != null
                && offerDestination1 != null
                && offerDestination2 != null
                && offerDestination3 != null
                && offerDestination4 != null
                && offerTitle1 != null
                && offerTitle2 != null
                && offerTitle3 != null
                && offerTitle4 != null
                && offerDescription1 != null
                && offerDescription2 != null
                && offerDescription3 != null
                && offerDescription4 != null
                && offerPrice1 != null
                && offerPrice2 != null
                && offerPrice3 != null
                && offerPrice4 != null
                && agencyLogo1 != null
                && agencyLogo2 != null
                && agencyLogo3 != null
                && agencyLogo4 != null
                && agencyName1 != null
                && agencyName2 != null
                && agencyName3 != null
                && agencyName4 != null;
    }

    private void loadAgencyAccounts() {
        try {
            agencyByResponsable.clear();
            for (AgencyAccount agency : agencyAccountService.findAll()) {
                if (agency.getResponsableId() != null) {
                    agencyByResponsable.put(agency.getResponsableId(), agency);
                }
            }
        } catch (SQLException ignored) {
            agencyByResponsable.clear();
        }
    }

    private void loadOffers() {
        allOffers = mockOffers();
    }

    private List<GuestOffer> mockOffers() {
        List<GuestOffer> list = new ArrayList<>();
        list.add(mock(1L, "Maldives Escape", "Crystal lagoons and overwater villas.", 4290.0, "Maldives", 12));
        list.add(mock(2L, "Santorini Getaway", "Sunset cliffs, white villages, sea breeze.", 3590.0, "Santorini", 7));
        list.add(mock(3L, "Paris Highlights", "City lights, museums, and Seine evenings.", 3190.0, "Paris", 3));
        list.add(mock(4L, "Dubai Luxury", "Skyline suites and desert signature tours.", 5390.0, "Dubai", 10));
        list.add(mock(5L, "Rome City Escape", "History, cuisine, and iconic landmarks.", 2890.0, "Rome", 5));
        list.add(mock(6L, "Bali Family Adventure", "Beach relaxation with curated activities.", 3890.0, "Bali", 8));
        return list;
    }

    private GuestOffer mock(Long id, String title, String description, Double price, String destination, Integer createdBy) {
        return new GuestOffer(id, title, description, price, destination, createdBy);
    }

    private void setupCardClipping() {
        clipCard(featuredCard1, offerImage1);
        clipCard(featuredCard2, offerImage2);
        clipCard(featuredCard3, offerImage3);
        clipCard(featuredCard4, offerImage4);
    }

    private void clipCard(Region card, ImageView image) {
        if (card == null || image == null) {
            return;
        }
        image.setFitWidth(CARD_W);
        image.setFitHeight(CARD_IMAGE_H);
        Rectangle clip = new Rectangle();
        clip.setWidth(CARD_W);
        clip.setHeight(CARD_IMAGE_H);
        clip.setArcWidth(CARD_ARC);
        clip.setArcHeight(CARD_ARC);
        image.setClip(clip);
    }

    private void setupAgencyLogoClipping() {
        clipAgencyLogo(agencyLogo1);
        clipAgencyLogo(agencyLogo2);
        clipAgencyLogo(agencyLogo3);
        clipAgencyLogo(agencyLogo4);
    }

    private void clipAgencyLogo(ImageView logo) {
        if (logo == null) {
            return;
        }
        Circle clip = new Circle(15, 15, 15);
        logo.setClip(clip);
    }

    private void installSearchShellFocusState() {
        if (searchField == null || offersSearchShell == null) {
            return;
        }
        searchField.focusedProperty().addListener((obs, oldVal, focused) -> {
            if (focused) {
                if (!offersSearchShell.getStyleClass().contains("offers-search-shell-focused")) {
                    offersSearchShell.getStyleClass().add("offers-search-shell-focused");
                }
            } else {
                offersSearchShell.getStyleClass().remove("offers-search-shell-focused");
            }
        });
    }

    private void renderFeaturedOffers(List<GuestOffer> offers) {
        GuestOffer o1 = offers.size() > 0 ? offers.get(0) : mock(101L, "Maldives Escape", "Crystal lagoons and overwater villas.", 4290.0, "Maldives", 12);
        GuestOffer o2 = offers.size() > 1 ? offers.get(1) : mock(102L, "Santorini Getaway", "Sunset cliffs, white villages, sea breeze.", 3590.0, "Santorini", 7);
        GuestOffer o3 = offers.size() > 2 ? offers.get(2) : mock(103L, "Paris Highlights", "City lights, museums, and Seine evenings.", 3190.0, "Paris", 99);
        GuestOffer o4 = offers.size() > 3 ? offers.get(3) : mock(104L, "Members Collection", "Private itineraries and invite-only curation.", 7990.0, "Exclusive", 1);

        applyOffer(o1, offerImage1, offerDestination1, offerTitle1, offerDescription1, offerPrice1, agencyLogo1, agencyName1);
        applyOffer(o2, offerImage2, offerDestination2, offerTitle2, offerDescription2, offerPrice2, agencyLogo2, agencyName2);
        applyOffer(o3, offerImage3, offerDestination3, offerTitle3, offerDescription3, offerPrice3, agencyLogo3, agencyName3);
        applyOffer(o4, offerImage4, offerDestination4, offerTitle4, offerDescription4, offerPrice4, agencyLogo4, agencyName4);
    }

    private void applyOffer(GuestOffer offer, ImageView image, Label destination, Label title, Label description, Label price,
                            ImageView agencyLogo, Label agencyName) {
        image.setImage(loadImage(resolveImage(offer)));
        destination.setText(safeText(offer.destination(), "Destination"));
        title.setText(safeText(offer.title(), "Travel Offer"));
        description.setText(safeText(offer.description(), "Curated premium experience."));
        price.setText(formatPrice(offer.price()));
        applyAgency(offer.createdBy(), agencyLogo, agencyName);
    }

    private void applyAgency(Integer createdBy, ImageView logoView, Label nameLabel) {
        nameLabel.setText("Partner agency");
        logoView.setImage(loadImage(IMG_MALDIVES));
        if (createdBy == null) {
            return;
        }
        AgencyAccount agency = agencyByResponsable.get(createdBy);
        if (agency == null) {
            return;
        }
        nameLabel.setText(safeText(agency.getAgencyName(), "Partner agency"));
        Image logo = resolveAgencyLogo(agency);
        if (logo != null) {
            logoView.setImage(logo);
        }
    }

    private Image resolveAgencyLogo(AgencyAccount agency) {
        if (agency.getId() == null) {
            return null;
        }
        if (agencyLogoCache.containsKey(agency.getId())) {
            return agencyLogoCache.get(agency.getId());
        }
        try {
            var assetOpt = agencyAccountService.loadAgencyProfileImage(agency.getId());
            if (assetOpt.isPresent()) {
                ImageAsset asset = assetOpt.get();
                if (asset.getData() != null && asset.getData().length > 0) {
                    Image image = new Image(new ByteArrayInputStream(asset.getData()));
                    agencyLogoCache.put(agency.getId(), image);
                    return image;
                }
            }
        } catch (SQLException ignored) {
            return null;
        }
        return null;
    }

    private String resolveImage(GuestOffer offer) {
        String key = safeText(offer.destination(), "").toLowerCase(Locale.ROOT);
        if (key.contains("paris") || key.contains("rome")) {
            return IMG_PARIS;
        }
        if (key.contains("maldives") || key.contains("bali") || key.contains("santorini")) {
            return IMG_MALDIVES;
        }
        return IMG_HERO;
    }

    private Image loadImage(String path) {
        var url = OffersGuestController.class.getResource(path);
        if (url == null) {
            return new Image("https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=1200&q=85", true);
        }
        return new Image(url.toExternalForm(), 900, 700, false, true, true);
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String formatPrice(Double price) {
        return price == null ? "Price on request" : String.format(Locale.US, "$%.0f", price);
    }

    @FXML
    private void onSearchInput() {
        if (searchField == null || !hasOffersUi()) {
            return;
        }
        String query = (searchField.getText() == null ? "" : searchField.getText()).toLowerCase(Locale.ROOT).trim();
        List<GuestOffer> filtered = allOffers.stream().filter(o -> {
            return query.isEmpty()
                    || safeText(o.title(), "").toLowerCase(Locale.ROOT).contains(query)
                    || safeText(o.description(), "").toLowerCase(Locale.ROOT).contains(query)
                    || safeText(o.destination(), "").toLowerCase(Locale.ROOT).contains(query);
        }).collect(Collectors.toList());

        renderFeaturedOffers(filtered);
    }

    private void promptLoginRequired() {
        Notifications.create()
                .title("Sign-in required")
                .text("Sign in to unlock offer details.")
                .hideAfter(Duration.seconds(2.2))
                .showInformation();

        ButtonType signIn = new ButtonType("Sign in", ButtonBar.ButtonData.OK_DONE);
        ButtonType signUp = new ButtonType("Sign up", ButtonBar.ButtonData.OTHER);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.INFORMATION, "", signIn, signUp, cancel);
        alert.setTitle("Sign-in required");
        alert.setHeaderText("This offer is displayed in guest preview mode.");
        alert.setContentText("Sign in or create an account to view full details.");
        alert.showAndWait().ifPresent(choice -> {
            if (choice == signIn) {
                NavigationManager.getInstance().showLogin();
            } else if (choice == signUp) {
                NavigationManager.getInstance().showRegister();
            }
        });
    }

    @FXML
    private void onFeedbacks() {
        NavigationManager.getInstance().showGuestFeedbacks();
    }

    @FXML
    private void onCrew() {
        NavigationManager.getInstance().showGuestCrew();
    }

    @FXML
    private void onSignIn() {
        NavigationManager.getInstance().showLogin();
    }

    @FXML
    private void onSignUp() {
        NavigationManager.getInstance().showRegister();
    }

    @FXML
    private void onOpenOffer1() {
        promptLoginRequired();
    }

    @FXML
    private void onOpenOffer2() {
        promptLoginRequired();
    }

    @FXML
    private void onOpenOffer3() {
        promptLoginRequired();
    }

    @FXML
    private void onOpenOffer4() {
        promptLoginRequired();
    }
}

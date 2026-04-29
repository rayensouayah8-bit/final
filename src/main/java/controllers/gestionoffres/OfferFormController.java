package controllers.gestionoffres;

import controllers.home.SignedInPageControllerBase;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import models.gestionoffres.TravelOffer;
import services.ServiceTravelOffer;
import utils.SessionManager;
import utils.StarfieldHelper;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class OfferFormController extends SignedInPageControllerBase {
    private static final String DEFAULT_OFFER_IMAGE = "/images/default_offer.png";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @FXML private Pane signedInCosmicStarfieldPane;
    @FXML private FlowPane layoutFlowPane;
    @FXML private Label titleLabel;
    @FXML private TextField titleField;
    @FXML private Label titleErrorLabel;
    @FXML private FlowPane countriesTagsPane;
    @FXML private TextField countriesSearchField;
    @FXML private Label countriesErrorLabel;
    @FXML private TextArea descriptionArea;
    @FXML private DatePicker departureDatePicker;
    @FXML private DatePicker returnDatePicker;
    @FXML private Label dateErrorLabel;
    @FXML private TextField priceField;
    @FXML private ComboBox<String> currencyCombo;
    @FXML private TextField seatsField;
    @FXML private Label numbersErrorLabel;
    @FXML private ImageView previewImageView;
    @FXML private Label previewTitleLabel;
    @FXML private FlowPane previewCountriesPane;
    @FXML private Label previewPriceLabel;
    @FXML private Label previewSeatsLabel;
    @FXML private Label previewDatesLabel;
    @FXML private Label photoFileNameLabel;
    @FXML private Label statusLabel;

    private final SessionManager session = SessionManager.getInstance();
    private final ServiceTravelOffer service = new ServiceTravelOffer();
    private TravelOffer editingOffer;
    private File selectedImage;
    private final List<String> selectedCountries = new ArrayList<>();
    private Popup countriesPopup;
    private javafx.scene.control.ListView<String> countriesPopupList;
    private static final List<String> PRESET_COUNTRIES = List.of(
            "Afghanistan", "Afrique du Sud", "Albanie", "Algérie", "Allemagne", "Andorre", "Angola",
            "Antigua-et-Barbuda", "Arabie Saoudite", "Argentine", "Arménie", "Australie", "Autriche",
            "Azerbaïdjan", "Bahamas", "Bahreïn", "Bangladesh", "Barbade", "Bélarus", "Belgique", "Belize",
            "Bénin", "Bhoutan", "Bolivie", "Bosnie-Herzégovine", "Botswana", "Brésil", "Brunei",
            "Bulgarie", "Burkina Faso", "Burundi", "Cabo Verde", "Cambodge", "Cameroun", "Canada",
            "Chili", "Chine", "Chypre", "Colombie", "Comores", "Congo", "Corée du Nord", "Corée du Sud",
            "Costa Rica", "Côte d'Ivoire", "Croatie", "Cuba", "Danemark", "Djibouti", "Dominique",
            "Egypte", "El Salvador", "Emirats Arabes Unis", "Equateur", "Erythrée", "Espagne",
            "Eswatini", "Estonie", "Ethiopie", "Fidji", "Finlande", "France", "Gabon", "Gambie",
            "Géorgie", "Ghana", "Grèce", "Grenade", "Guatemala", "Guinée", "Guinée-Bissau",
            "Guinée équatoriale", "Guyana", "Haïti", "Honduras", "Hongrie", "Iles Marshall",
            "Iles Salomon", "Inde", "Indonésie", "Irak", "Iran", "Irlande", "Islande", "Israël",
            "Italie", "Jamaïque", "Japon", "Jordanie", "Kazakhstan", "Kenya", "Kirghizistan", "Kiribati",
            "Koweït", "Laos", "Lesotho", "Lettonie", "Liban", "Liberia", "Libye", "Liechtenstein",
            "Lituanie", "Luxembourg", "Macédoine du Nord", "Madagascar", "Malaisie", "Malawi",
            "Maldives", "Mali", "Malte", "Maroc", "Maurice", "Mauritanie", "Mexique", "Micronésie",
            "Moldavie", "Monaco", "Mongolie", "Monténégro", "Mozambique", "Myanmar", "Namibie",
            "Nauru", "Népal", "Nicaragua", "Niger", "Nigeria", "Norvège", "Nouvelle-Zélande", "Oman",
            "Ouganda", "Ouzbékistan", "Pakistan", "Palaos", "Palestine", "Panama", "Papouasie-Nouvelle-Guinée",
            "Paraguay", "Pays-Bas", "Pérou", "Philippines", "Pologne", "Portugal", "Qatar",
            "République Centrafricaine", "République Démocratique du Congo", "République Dominicaine",
            "République Tchèque", "Roumanie", "Royaume-Uni", "Russie", "Rwanda",
            "Saint-Kitts-et-Nevis", "Saint-Vincent-et-les-Grenadines", "Sainte-Lucie",
            "Saint-Marin", "Samoa", "São Tomé-et-Príncipe", "Sénégal", "Serbie", "Seychelles",
            "Sierra Leone", "Singapour", "Slovaquie", "Slovénie", "Somalie", "Soudan", "Soudan du Sud",
            "Sri Lanka", "Suède", "Suisse", "Suriname", "Syrie", "Tadjikistan", "Tanzanie", "Tchad",
            "Thaïlande", "Timor oriental", "Togo", "Tonga", "Trinité-et-Tobago", "Tunisie",
            "Turkménistan", "Turquie", "Tuvalu", "Ukraine", "Uruguay", "Vanuatu", "Vatican",
            "Venezuela", "Vietnam", "Yémen", "Zambie", "Zimbabwe"
    ).stream().sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());

    private static final String INPUT_STYLE_BLUR = "-fx-background-color: #1C1535; -fx-border-color: #3B2F6B; -fx-border-width: 1.5; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 10 14; -fx-text-fill: white; -fx-font-size: 14;";
    private static final String INPUT_STYLE_FOCUS = "-fx-background-color: #1C1535; -fx-border-color: #7C3AED; -fx-border-width: 1.5; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 10 14; -fx-text-fill: white; -fx-font-size: 14;";
    private static final String TEXTAREA_STYLE_BLUR = "-fx-control-inner-background: #1C1535; -fx-background-color: #1C1535; -fx-border-color: #3B2F6B; -fx-border-width: 1.5; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 10 14; -fx-text-fill: white; -fx-font-size: 14;";
    private static final String TEXTAREA_STYLE_FOCUS = "-fx-control-inner-background: #1C1535; -fx-background-color: #1C1535; -fx-border-color: #7C3AED; -fx-border-width: 1.5; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 10 14; -fx-text-fill: white; -fx-font-size: 14;";
    private static final String COMBO_STYLE = "-fx-background-color: #1C1535; -fx-border-color: #3B2F6B; -fx-border-width: 1.5; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 6 10; -fx-text-fill: white; -fx-font-size: 14;";
    private static final String COMBO_FOCUS_STYLE = "-fx-background-color: #1C1535; -fx-border-color: #7C3AED; -fx-border-width: 1.5; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 6 10; -fx-text-fill: white; -fx-font-size: 14;";

    @FXML
    private void initialize() {
        initSignedInSidebar();
        StarfieldHelper.populate(signedInCosmicStarfieldPane);
        if (!"ROLE_AGENCY".equalsIgnoreCase(session.getRole())) {
            setStatus("Access denied", true);
            return;
        }
        currencyCombo.getItems().setAll("EUR", "USD", "GBP", "TND");
        currencyCombo.setValue("EUR");
        applyInputStyling();
        setupCountriesSelector();
        editingOffer = session.getSelectedOffer();
        if (editingOffer != null) {
            titleLabel.setText("Modifier une offre");
            fillForm(editingOffer);
        } else {
            titleLabel.setText("Créer une offre");
            var fallback = getClass().getResource(DEFAULT_OFFER_IMAGE);
            if (fallback != null) {
                previewImageView.setImage(new Image(fallback.toExternalForm()));
            }
        }
        installLivePreview();
        Platform.runLater(() -> applyResponsiveWrap(titleField.getScene() != null ? titleField.getScene().getWidth() : 1400));
    }

    @FXML
    private void onChooseImage() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png")
        );
        selectedImage = chooser.showOpenDialog(titleField.getScene().getWindow());
        if (selectedImage != null) {
            previewImageView.setImage(new Image(selectedImage.toURI().toString()));
            photoFileNameLabel.setText(selectedImage.getName());
        }
    }

    @FXML
    private void onSave() {
        try {
            clearErrors();
            TravelOffer offer = editingOffer == null ? new TravelOffer() : editingOffer;
            mapForm(offer);
            if (editingOffer == null) {
                offer.setAgencyId(session.getAgencyId());
                offer.setCreatedById(session.getUserId());
                offer.setApprovalStatus("pending");
                service.add(offer);
            } else {
                service.update(offer);
            }
            goBack();
        } catch (Exception e) {
            handleValidationError(e.getMessage());
        }
    }

    @FXML
    private void onBack() {
        goBack();
    }

    private void mapForm(TravelOffer offer) throws IOException {
        String title = safe(titleField.getText());
        String countries = String.join(",", selectedCountries);
        double price = parseDoubleSafe(priceField.getText());
        int seats = parseIntSafe(seatsField.getText());
        LocalDate departure = departureDatePicker.getValue();
        LocalDate returning = returnDatePicker.getValue();
        if (title.isBlank()) throw new IllegalArgumentException("Titre requis");
        if (countries.isBlank()) throw new IllegalArgumentException("Pays requis");
        if (price <= 0) throw new IllegalArgumentException("Prix invalide");
        if (seats <= 0) throw new IllegalArgumentException("Places invalides");
        if (departure == null || !departure.isAfter(LocalDate.now())) throw new IllegalArgumentException("Date départ invalide");
        if (returning == null || !returning.isAfter(departure)) throw new IllegalArgumentException("Date retour invalide");

        offer.setTitle(title);
        offer.setCountries(countries);
        offer.setDescription(descriptionArea.getText());
        offer.setDepartureDate(departure.atStartOfDay());
        offer.setReturnDate(returning.atStartOfDay());
        offer.setPrice(price);
        offer.setCurrency(currencyCombo.getValue());
        offer.setAvailableSeats(seats);
        if (selectedImage != null) {
            offer.setImage(service.saveImage(selectedImage));
        }
        if (offer.getImage() == null || offer.getImage().isBlank()) {
            offer.setImage(null);
        }
    }

    private void fillForm(TravelOffer offer) {
        titleField.setText(offer.getTitle());
        selectedCountries.clear();
        selectedCountries.addAll(parseCountries(offer.getCountries()));
        renderCountryTags();
        descriptionArea.setText(offer.getDescription());
        if (offer.getDepartureDate() != null) departureDatePicker.setValue(offer.getDepartureDate().toLocalDate());
        if (offer.getReturnDate() != null) returnDatePicker.setValue(offer.getReturnDate().toLocalDate());
        priceField.setText(offer.getPrice() == null ? "" : String.valueOf(offer.getPrice()));
        currencyCombo.setValue(offer.getCurrency() == null ? "EUR" : offer.getCurrency());
        seatsField.setText(offer.getAvailableSeats() == null ? "" : String.valueOf(offer.getAvailableSeats()));
        if (offer.getImage() != null) {
            var url = getClass().getResource(offer.getImage());
            if (url != null) previewImageView.setImage(new Image(url.toExternalForm()));
            photoFileNameLabel.setText(offer.getImage());
        } else {
            var fallback = getClass().getResource(DEFAULT_OFFER_IMAGE);
            if (fallback != null) {
                previewImageView.setImage(new Image(fallback.toExternalForm()));
            }
        }
    }

    private void goBack() {
        session.setSelectedOffer(null);
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/gestionoffres/Offers.fxml"));
            Scene scene = titleField.getScene();
            scene.setRoot(root);
        } catch (IOException e) {
            setStatus("Navigation failed: " + e.getMessage(), true);
        }
    }

    private void setStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: " + (error ? "#FCA5A5" : "#A78BFA"));
    }

    private void installLivePreview() {
        titleField.textProperty().addListener((o, ov, nv) -> renderPreview());
        priceField.textProperty().addListener((o, ov, nv) -> renderPreview());
        seatsField.textProperty().addListener((o, ov, nv) -> renderPreview());
        departureDatePicker.valueProperty().addListener((o, ov, nv) -> renderPreview());
        returnDatePicker.valueProperty().addListener((o, ov, nv) -> renderPreview());
        currencyCombo.valueProperty().addListener((o, ov, nv) -> renderPreview());
        if (layoutFlowPane != null) {
            layoutFlowPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    newScene.widthProperty().addListener((o, oldW, newW) -> applyResponsiveWrap(newW.doubleValue()));
                    applyResponsiveWrap(newScene.getWidth());
                }
            });
        }
        renderPreview();
    }

    private void renderPreview() {
        previewTitleLabel.setText(safe(titleField.getText()).isBlank() ? "Titre de l'offre" : safe(titleField.getText()));
        previewCountriesPane.getChildren().setAll(buildCountryPills(String.join(",", selectedCountries)));
        double price = parseDoubleSafe(priceField.getText());
        String currency = safe(currencyCombo.getValue()).isBlank() ? "EUR" : currencyCombo.getValue();
        previewPriceLabel.setText(String.format(Locale.US, "%.2f %s", price, currency));
        previewSeatsLabel.setText("Places: " + (parseIntSafe(seatsField.getText()) <= 0 ? 0 : parseIntSafe(seatsField.getText())));
        previewDatesLabel.setText(formatDates(departureDatePicker.getValue(), returnDatePicker.getValue()));
    }

    private List<Label> buildCountryPills(String csv) {
        List<Label> pills = new ArrayList<>();
        for (String c : safe(csv).split(",")) {
            String country = c.trim();
            if (country.isBlank()) {
                continue;
            }
            Label pill = new Label(country);
            pill.setStyle("-fx-background-color: #7C3AEDAA; -fx-text-fill: white; -fx-background-radius: 999; -fx-padding: 4 10;");
            pills.add(pill);
        }
        return pills;
    }

    private String formatDates(LocalDate departure, LocalDate returning) {
        if (departure == null || returning == null) {
            return "Dates: N/A";
        }
        return "Dates: " + DATE_FMT.format(departure) + " -> " + DATE_FMT.format(returning);
    }

    private void applyResponsiveWrap(double sceneWidth) {
        if (layoutFlowPane == null || sceneWidth <= 0) {
            return;
        }
        layoutFlowPane.setPrefWrapLength(Math.max(760, sceneWidth - 360));
    }

    private void clearErrors() {
        hideError(titleErrorLabel);
        hideError(countriesErrorLabel);
        hideError(dateErrorLabel);
        hideError(numbersErrorLabel);
    }

    private void handleValidationError(String message) {
        if (message == null) {
            setStatus("Validation error", true);
            return;
        }
        if (message.contains("Titre")) {
            showError(titleErrorLabel, "Le titre est obligatoire.");
        } else if (message.contains("Pays")) {
            showError(countriesErrorLabel, "Veuillez saisir au moins un pays.");
        } else if (message.contains("Date")) {
            showError(dateErrorLabel, "Vérifiez les dates de départ et retour.");
        } else if (message.contains("Prix") || message.contains("Places")) {
            showError(numbersErrorLabel, "Le prix et les places doivent être valides.");
        } else {
            setStatus(message, true);
        }
    }

    private void showError(Label label, String message) {
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }

    private void hideError(Label label) {
        label.setText("");
        label.setVisible(false);
        label.setManaged(false);
    }

    private String safe(String v) {
        return v == null ? "" : v.trim();
    }

    private int parseIntSafe(String v) {
        try {
            return Integer.parseInt(safe(v));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private double parseDoubleSafe(String v) {
        try {
            return Double.parseDouble(safe(v));
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private void applyInputStyling() {
        styleFocusable(titleField, INPUT_STYLE_BLUR, INPUT_STYLE_FOCUS);
        styleFocusable(descriptionArea, TEXTAREA_STYLE_BLUR, TEXTAREA_STYLE_FOCUS);
        styleFocusable(priceField, INPUT_STYLE_BLUR, INPUT_STYLE_FOCUS);
        styleFocusable(seatsField, INPUT_STYLE_BLUR, INPUT_STYLE_FOCUS);

        departureDatePicker.setStyle(INPUT_STYLE_BLUR);
        departureDatePicker.focusedProperty().addListener((obs, oldV, focused) ->
                departureDatePicker.setStyle(focused ? INPUT_STYLE_FOCUS : INPUT_STYLE_BLUR));

        returnDatePicker.setStyle(INPUT_STYLE_BLUR);
        returnDatePicker.focusedProperty().addListener((obs, oldV, focused) ->
                returnDatePicker.setStyle(focused ? INPUT_STYLE_FOCUS : INPUT_STYLE_BLUR));

        currencyCombo.setStyle(COMBO_STYLE);
        currencyCombo.focusedProperty().addListener((obs, oldV, focused) ->
                currencyCombo.setStyle(focused ? COMBO_FOCUS_STYLE : COMBO_STYLE));
    }

    private void styleFocusable(javafx.scene.control.Control c, String blurStyle, String focusStyle) {
        c.setStyle(blurStyle);
        c.focusedProperty().addListener((obs, oldV, focused) -> c.setStyle(focused ? focusStyle : blurStyle));
    }

    private void setupCountriesSelector() {
        countriesPopupList = new javafx.scene.control.ListView<>();
        countriesPopupList.setMaxHeight(220);
        countriesPopupList.setPrefHeight(220);
        countriesPopupList.setFixedCellSize(36);
        countriesPopupList.setStyle("-fx-control-inner-background: #1C1535; -fx-background-color: #1C1535; -fx-border-color: #3B2F6B; -fx-border-width: 1; -fx-border-radius: 0 0 10 10; -fx-background-radius: 0 0 10 10;");
        countriesPopupList.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                    return;
                }
                setText(item);
                setStyle("-fx-text-fill: white; -fx-font-size: 13px;");
                hoverProperty().addListener((obs, ov, hv) ->
                        setStyle((hv || isSelected())
                                ? "-fx-background-color: #7C3AED; -fx-text-fill: white; -fx-font-size: 13px;"
                                : "-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 13px;"));
            }
        });
        countriesPopup = new Popup();
        countriesPopup.getContent().add(countriesPopupList);
        countriesPopup.setAutoHide(true);
        countriesPopup.setHideOnEscape(true);

        countriesSearchField.textProperty().addListener((obs, oldV, newV) -> refreshCountrySuggestions(newV));
        countriesSearchField.setOnAction(e -> {
            addCountryTag(countriesSearchField.getText());
            countriesSearchField.clear();
            hideSuggestions();
        });
        countriesPopupList.setOnMouseClicked(e -> {
            String selected = countriesPopupList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                addCountryTag(selected);
                countriesSearchField.clear();
                hideSuggestions();
            }
        });
        countriesSearchField.focusedProperty().addListener((obs, oldV, focused) -> {
            if (focused) {
                refreshCountrySuggestions(countriesSearchField.getText());
                countriesTagsPane.setStyle("-fx-background-color: #1C1535; -fx-border-color: #7C3AED; -fx-border-width: 1.5; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 10 14;");
            } else {
                countriesTagsPane.setStyle("-fx-background-color: #1C1535; -fx-border-color: #3B2F6B; -fx-border-width: 1.5; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 10 14;");
            }
        });
        renderCountryTags();
    }

    private void refreshCountrySuggestions(String query) {
        String q = safe(query).toLowerCase(Locale.ROOT);
        List<String> matches = PRESET_COUNTRIES.stream()
                .filter(c -> c.toLowerCase(Locale.ROOT).contains(q))
                .filter(c -> selectedCountries.stream().noneMatch(s -> s.equalsIgnoreCase(c)))
                .limit(5)
                .collect(Collectors.toList());
        countriesPopupList.getItems().setAll(matches);
        boolean visible = !matches.isEmpty() && countriesSearchField.isFocused();
        if (visible) {
            showSuggestionsPopup();
        } else {
            hideSuggestions();
        }
    }

    private void hideSuggestions() {
        if (countriesPopup != null) {
            countriesPopup.hide();
        }
    }

    private void showSuggestionsPopup() {
        if (countriesPopup == null || countriesSearchField.getScene() == null || countriesSearchField.getScene().getWindow() == null) {
            return;
        }
        var bounds = countriesSearchField.localToScreen(countriesSearchField.getBoundsInLocal());
        if (bounds == null) {
            return;
        }
        countriesPopupList.setPrefWidth(Math.max(320, countriesTagsPane.getWidth()));
        if (!countriesPopup.isShowing()) {
            countriesPopup.show(countriesSearchField.getScene().getWindow(), bounds.getMinX(), bounds.getMaxY() + 4);
        } else {
            countriesPopup.setX(bounds.getMinX());
            countriesPopup.setY(bounds.getMaxY() + 4);
        }
    }

    private void addCountryTag(String value) {
        String country = safe(value);
        if (country.isBlank()) {
            return;
        }
        boolean exists = selectedCountries.stream().anyMatch(c -> c.equalsIgnoreCase(country));
        if (exists) {
            return;
        }
        selectedCountries.add(country);
        renderCountryTags();
    }

    private void removeCountryTag(String value) {
        selectedCountries.removeIf(c -> c.equalsIgnoreCase(value));
        renderCountryTags();
    }

    private void renderCountryTags() {
        countriesTagsPane.getChildren().clear();
        for (String country : selectedCountries) {
            Label txt = new Label(country);
            txt.setStyle("-fx-text-fill: white;");
            Button remove = new Button("✕");
            remove.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-padding: 0 0 0 4;");
            remove.setOnAction(e -> removeCountryTag(country));
            HBox tag = new HBox(4, txt, remove);
            tag.setStyle("-fx-background-color: #7C3AED; -fx-background-radius: 20; -fx-padding: 4 10;");
            countriesTagsPane.getChildren().add(tag);
        }
        countriesTagsPane.getChildren().add(countriesSearchField);
        renderPreview();
    }

    private List<String> parseCountries(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String cleaned = raw.replace("[", "").replace("]", "").replace("\"", "");
        return Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

}

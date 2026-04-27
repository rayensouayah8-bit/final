"""One-off: inline signed-in sidebar into all signed-in FXML files."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/fxml"

OLD_LEFT = """    <left>
        <StackPane maxHeight="Infinity" maxWidth="348.0" minWidth="308.0" prefWidth="326.0"
                   styleClass="signed-in-home-sidebar-host signed-in-shell-sidebar-host" BorderPane.alignment="TOP_LEFT">
            <children>
                <fx:include source="/fxml/components/signed-in-sidebar.fxml" maxHeight="Infinity" maxWidth="Infinity"/>
            </children>
        </StackPane>
    </left>"""

NEW_LEFT = """    <left>
        <StackPane maxHeight="Infinity" maxWidth="348.0" minWidth="308.0" prefWidth="326.0"
                   styleClass="signed-in-home-sidebar-host signed-in-shell-sidebar-host"
                   style="-fx-background-color: linear-gradient(to bottom, #07112E 0%, #081430 48%, #0A1635 100%); -fx-padding: 0;"
                   BorderPane.alignment="TOP_LEFT">
            <children>
                <VBox spacing="10.0" styleClass="sv-si-sidebar" maxHeight="Infinity" maxWidth="Infinity"
                       style="-fx-background-color: transparent;">
                    <children>
                        <VBox spacing="10.0" styleClass="sv-si-brand" VBox.vgrow="NEVER">
                            <children>
                                <Label text="Smart Voyage" styleClass="sv-si-brand-title"/>
                                <Region styleClass="sv-si-brand-accent"/>
                                <Label text="Desktop travel UI" styleClass="sv-si-brand-strap"/>
                                <Label fx:id="signedInSidebarSessionLabel" text="" styleClass="sv-si-session"/>
                            </children>
                        </VBox>
                        <Region styleClass="sv-si-hrule"/>
                        <Separator/>
                        <Button fx:id="navHomeButton" text="Home" onAction="#onHome" styleClass="sv-si-nav-btn"/>
                        <Button fx:id="navOffersButton" text="Offers" onAction="#onOffres" styleClass="sv-si-nav-btn"/>
                        <Button fx:id="navAgenciesButton" text="Agencies" onAction="#onAgences" styleClass="sv-si-nav-btn"/>
                        <Button fx:id="navMessagesButton" text="Messages" onAction="#onMessagerie" styleClass="sv-si-nav-btn"/>
                        <Button fx:id="navRecommendationsButton" text="Recommendations" onAction="#onRecommandation" styleClass="sv-si-nav-btn"/>
                        <Button fx:id="navEventsButton" text="Events" onAction="#onEvenement" styleClass="sv-si-nav-btn"/>
                        <Region VBox.vgrow="ALWAYS"/>
                        <Separator/>
                        <Button fx:id="navPremiumButton" text="Premium" onAction="#onPremium" styleClass="sv-si-nav-btn sv-si-nav-btn-cta"/>
                        <Button fx:id="navNotificationsButton" text="Notifications" onAction="#onNotifications" styleClass="sv-si-nav-btn"/>
                        <Button fx:id="navProfileButton" text="My Profile" onAction="#onProfile" styleClass="sv-si-nav-btn"/>
                        <Button fx:id="navDashboardButton" text="Dashboard IA" onAction="#onDashboardIa" styleClass="sv-si-nav-btn"/>
                        <Separator/>
                        <HBox alignment="CENTER_LEFT" spacing="10.0" styleClass="sv-si-footer">
                            <children>
                                <Button onAction="#onThemeToggle" text="◐" accessibleText="Toggle theme"
                                        styleClass="sv-si-nav-btn sv-si-nav-btn-ghost"/>
                                <Region HBox.hgrow="ALWAYS"/>
                                <Button text="Logout" onAction="#onLogout" styleClass="sv-si-nav-btn"/>
                            </children>
                        </HBox>
                    </children>
                </VBox>
            </children>
        </StackPane>
    </left>"""

OLD_CENTER = '<StackPane styleClass="signed-in-home-main-canvas signed-in-cosmic-canvas" maxWidth="Infinity" maxHeight="Infinity">'
NEW_CENTER = (
    '<StackPane styleClass="signed-in-home-main-canvas signed-in-cosmic-canvas" maxWidth="Infinity" maxHeight="Infinity"\n'
    '                   style="-fx-background-color: linear-gradient(to bottom, #2A174A 0%, #17052D 52%, #05010D 100%);">'
)

SEP_IMPORT = '<?import javafx.scene.control.Separator?>'

paths = list(ROOT.rglob("*.fxml"))
patched = 0
for p in paths:
    t = p.read_text(encoding="utf-8")
    if OLD_LEFT not in t:
        continue
    t = t.replace(OLD_LEFT, NEW_LEFT)
    t = t.replace(OLD_CENTER, NEW_CENTER)
    if SEP_IMPORT not in t:
        lines = t.splitlines()
        insert_at = 0
        for i, line in enumerate(lines):
            if line.startswith("<?import javafx.scene.control."):
                insert_at = i + 1
        lines.insert(insert_at, SEP_IMPORT)
        t = "\n".join(lines) + "\n"
    p.write_text(t, encoding="utf-8")
    patched += 1
    print("patched", p.relative_to(ROOT))
print("total", patched)

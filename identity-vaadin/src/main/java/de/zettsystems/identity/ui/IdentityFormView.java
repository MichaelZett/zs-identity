package de.zettsystems.identity.ui;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.BoxSizing;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.UiSettings;

import java.time.Duration;
import java.util.Set;

/**
 * Gemeinsame Gestalt der mitgelieferten Ansichten — Anmeldung, Registrierung,
 * Passwort-Reset und die Einlöse-Ansicht.
 *
 * <p>Diese Seiten sind die <strong>ersten</strong>, die ein neues Mitglied
 * sieht, gehören aber dem Baustein und nicht der Anwendung. Sie können deren
 * Theme nicht kennen. Was sie stattdessen einhalten, ist eine Untergrenze, die
 * überall trägt:
 *
 * <ol>
 *   <li><strong>Eine Spalte, zentriert, mit Höchstbreite</strong>
 *       ({@code zs.identity.ui.max-width}, voreingestellt {@code 28rem}). Am
 *       Telefon füllt sie die Breite, am Rechner wächst sie nicht ins
 *       Unlesbare.</li>
 *   <li><strong>Kein Querscrollen bei 375 px.</strong> Dafür sorgen
 *       {@code border-box} samt Innenabstand und die Regel, dass jedes
 *       Eingabefeld und jeder Knopf die volle Spaltenbreite bekommt
 *       ({@link #addFullWidth}) — feste Pixelbreiten gibt es hier nicht.</li>
 *   <li><strong>Feste CSS-Klassen statt eigener Farben.</strong> Jede Ansicht
 *       trägt {@value #VIEW_CLASS} und eine eigene Kennung, dazu alles aus
 *       {@code zs.identity.ui.class-names}. Darüber stylt eine Anwendung mit,
 *       ohne dass der Baustein ihr Theme kennt — und ohne dass er ihr eines
 *       aufzwingt.</li>
 *   <li><strong>Keine Farbe von Hand.</strong> Hervorhebung läuft über
 *       Vaadins Varianten, nicht über gesetzte Farbwerte: Eigene Farben
 *       kollidieren mit dem dunklen Erscheinungsbild einer Anwendung, und
 *       Kontrastfragen kann der Baustein nicht für sie entscheiden.</li>
 * </ol>
 *
 * <p>Die Ansichten erben deshalb hierher, statt jede für sich Breite,
 * Abstände und Knöpfe zu setzen: Eine Regel, die an neun Stellen steht, ist
 * nach dem nächsten Umbau an sieben davon eine andere.
 */
public abstract class IdentityFormView extends VerticalLayout implements HasDynamicTitle {

    /** CSS-Klasse an jeder Ansicht des Bausteins — der Andockpunkt für eigenes CSS. */
    public static final String VIEW_CLASS = "identity-view";

    /**
     * Vaadins Varianten heißen weiterhin {@code LUMO_*}, obwohl das
     * Basistheme seit Vaadin 25 Aura ist. Sie stehen hier an einer Stelle
     * gebündelt: Sollte eine künftige Fassung sie fallen lassen, ist das eine
     * Änderung statt neun.
     */
    private static final ButtonVariant PRIMARY_VARIANT = ButtonVariant.LUMO_PRIMARY;
    private static final ButtonVariant TERTIARY_VARIANT = ButtonVariant.LUMO_TERTIARY;
    private static final NotificationVariant WARNING_VARIANT = NotificationVariant.LUMO_ERROR;

    /** Die Meldungsschlüssel, die jede Ansicht gleich behandelt. */
    private static final Set<String> COMMON_KEYS = Set.of(
            IdentityMessageKeys.TOKEN_INVALID,
            IdentityMessageKeys.TOKEN_EXPIRED,
            IdentityMessageKeys.EMAIL_ALREADY_REGISTERED,
            IdentityMessageKeys.SELF_REGISTRATION_DISABLED,
            IdentityMessageKeys.ACCOUNT_ALREADY_CLAIMED);

    private final IdentityTexts texts;
    private final UiSettings ui;
    private final int passwordMinLength;

    /**
     * @param viewName Kennung dieser Ansicht für die CSS-Klasse, etwa
     *                 {@code login}; daraus wird {@code identity-view--login}
     */
    protected IdentityFormView(IdentityMessages messages, IdentityProperties properties, String viewName) {
        this.texts = new IdentityTexts(messages, properties);
        this.ui = properties.ui();
        this.passwordMinLength = properties.passwordMinLength();

        setWidthFull();
        setMaxWidth(ui.maxWidth());
        // Ohne border-box addiert sich der Innenabstand auf die Breite — genau
        // das erzeugt am Telefon den waagerechten Balken.
        setBoxSizing(BoxSizing.BORDER_BOX);
        getStyle().set("margin-inline", "auto");
        addClassName(VIEW_CLASS);
        addClassName(VIEW_CLASS + "--" + viewName);
        ui.classNames().forEach(this::addClassName);
    }

    /** Die Texte dieser Ansicht in der Sprache der aufrufenden Person. */
    protected final IdentityTexts texts() {
        return texts;
    }

    /** Kurzform für {@code texts().get(key, args)} — der häufigste Aufruf überhaupt. */
    protected final String text(String key, Object... args) {
        return texts.get(key, args);
    }

    /**
     * Streckt die Ansicht über die ganze Seite und setzt den Inhalt in die
     * Mitte. Für die Anmeldung gedacht: Sie ist kurz genug, dass sie sonst
     * oben klebt.
     */
    protected final void centerOnPage() {
        setSizeFull();
        setMaxWidth("100%");
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
    }

    /**
     * Nimmt Komponenten auf und gibt ihnen die volle Spaltenbreite. Der
     * gesamte Grund, warum die Ansichten am Telefon nicht querscrollen —
     * darum gehört jedes Feld und jeder Knopf hier hindurch und nicht durch
     * {@code add(...)}.
     */
    protected final void addFullWidth(Component... components) {
        for (Component component : components) {
            add(fullWidth(component));
        }
    }

    /**
     * Dieselbe Regel für Komponenten, die woanders landen als direkt in der
     * Ansicht — etwa in der Spalte aus {@link #centeredColumn()}.
     */
    protected static <T extends Component> T fullWidth(T component) {
        if (component instanceof HasSize sized) {
            sized.setWidthFull();
        }
        return component;
    }

    /**
     * Eine Spalte in der Höchstbreite der Ansicht, bereits eingehängt.
     *
     * <p>Für die Ansichten, die selbst die ganze Seite einnehmen
     * ({@link #centerOnPage()}): Dort ist die Ansicht so breit wie der
     * Bildschirm, und ein Knopf über die volle Breite sähe am Rechner
     * verloren aus. Alles, was hineinkommt, gehört durch {@link #fullWidth}.
     */
    protected final VerticalLayout centeredColumn() {
        VerticalLayout column = new VerticalLayout();
        column.setWidthFull();
        column.setMaxWidth(ui.maxWidth());
        column.setPadding(false);
        column.addClassName(VIEW_CLASS + "__column");
        add(column);
        return column;
    }

    /** Überschrift einer Ansicht. */
    protected final H2 heading(String key, Object... args) {
        return new H2(text(key, args));
    }

    /** Fließtext einer Ansicht. */
    protected final Paragraph paragraph(String key, Object... args) {
        return new Paragraph(text(key, args));
    }

    /** Der Knopf, der die Ansicht abschließt — je Ansicht genau einer. */
    protected final Button primaryButton(String key, String id,
                                         ComponentEventListener<ClickEvent<Button>> listener) {
        Button button = new Button(text(key), listener);
        button.addThemeVariants(PRIMARY_VARIANT);
        button.setId(id);
        return button;
    }

    /** Ein Nebenweg, der die Ansicht verlässt, ohne etwas zu speichern. */
    protected final Button secondaryButton(String key, String id,
                                           ComponentEventListener<ClickEvent<Button>> listener) {
        Button button = new Button(text(key), listener);
        button.addThemeVariants(TERTIARY_VARIANT);
        button.setId(id);
        return button;
    }

    /** Knopf, der auf eine andere Route des Bausteins führt. */
    protected final Button navigationButton(String key, String route) {
        return new Button(text(key), event -> UI.getCurrent().navigate(route));
    }

    /**
     * Hinweis über dem Formular. Oben und mittig, weil das Formular selbst die
     * Spaltenmitte einnimmt — am unteren Rand stünde die Meldung am Telefon
     * hinter der Tastatur.
     */
    protected final void warn(String message) {
        Duration duration = ui.notificationDuration();
        Notification notification = Notification.show(message, (int) duration.toMillis(),
                Notification.Position.TOP_CENTER);
        notification.addThemeVariants(WARNING_VARIANT);
    }

    /**
     * Übersetzt die Meldungsschlüssel des Bausteins in einen Text.
     *
     * <p>Nur die bekannten: Ein unbekannter Schlüssel — etwa aus einem selbst
     * gebauten Dienst einer Anwendung — bekommt den allgemeinen Text statt
     * eines rohen Schlüssels auf dem Bildschirm.
     */
    protected final String translate(IdentityException e) {
        String key = e.getMessageKey();
        if (IdentityMessageKeys.PASSWORD_TOO_SHORT.equals(key)) {
            return text(key, passwordMinLength);
        }
        return COMMON_KEYS.contains(key) ? text(key) : text(IdentityMessageKeys.UNEXPECTED);
    }
}

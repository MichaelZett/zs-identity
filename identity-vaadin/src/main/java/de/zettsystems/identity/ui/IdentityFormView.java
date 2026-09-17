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
 * The shared shape of the shipped views: sign-in, registration, password reset
 * and the invitation redemption view.
 *
 * <p>These pages are the <strong>first</strong> ones a new member sees, yet
 * they belong to the building block and not to the application. They cannot
 * know its theme. What they keep to instead is a lower bound that works
 * everywhere:
 *
 * <ol>
 *   <li><strong>One column, centred, with a maximum width</strong>
 *       ({@code zs.identity.ui.max-width}, {@code 28rem} by default). On a
 *       phone it fills the width; on a desktop it does not grow into
 *       unreadability.</li>
 *   <li><strong>No horizontal scrolling at 375 px.</strong> That is ensured by
 *       {@code border-box} together with the padding, and by the rule that
 *       every input field and every button gets the full column width
 *       ({@link #addFullWidth}). There are no fixed pixel widths here.</li>
 *   <li><strong>Fixed CSS classes instead of colours of our own.</strong> Every
 *       view carries {@value #VIEW_CLASS} and an identifier of its own, plus
 *       everything from {@code zs.identity.ui.class-names}. An application
 *       styles through those without the building block knowing its theme --
 *       and without the building block forcing one on it.</li>
 *   <li><strong>No colour set by hand.</strong> Emphasis goes through Vaadin's
 *       variants rather than through explicit colour values: colours of our own
 *       clash with an application's dark appearance, and the building block
 *       cannot decide questions of contrast on its behalf.</li>
 * </ol>
 *
 * <p>The views therefore inherit from here instead of each setting width,
 * spacing and buttons for itself: a rule written down in nine places is a
 * different rule in seven of them after the next rework.
 */
public abstract class IdentityFormView extends VerticalLayout implements HasDynamicTitle {

    /** CSS class on every view of this building block; the hook for custom CSS. */
    public static final String VIEW_CLASS = "identity-view";

    /**
     * Vaadin's variants are still called {@code LUMO_*}, even though the base
     * theme has been Aura since Vaadin 25. They are gathered in one place here:
     * should a future version drop them, that is one change instead of nine.
     */
    private static final ButtonVariant PRIMARY_VARIANT = ButtonVariant.LUMO_PRIMARY;
    private static final ButtonVariant TERTIARY_VARIANT = ButtonVariant.LUMO_TERTIARY;
    private static final NotificationVariant WARNING_VARIANT = NotificationVariant.LUMO_ERROR;

    /** The message keys that every view handles in the same way. */
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
     * @param viewName identifier of this view for the CSS class, {@code login}
     *                 for example, which becomes {@code identity-view--login}
     */
    protected IdentityFormView(IdentityMessages messages, IdentityProperties properties, String viewName) {
        this.texts = new IdentityTexts(messages, properties);
        this.ui = properties.ui();
        this.passwordMinLength = properties.passwordMinLength();

        setWidthFull();
        setMaxWidth(ui.maxWidth());
        // Without border-box the padding adds to the width, and that is exactly
        // what produces the horizontal bar on a phone.
        setBoxSizing(BoxSizing.BORDER_BOX);
        getStyle().set("margin-inline", "auto");
        addClassName(VIEW_CLASS);
        addClassName(VIEW_CLASS + "--" + viewName);
        ui.classNames().forEach(this::addClassName);
    }

    /** The texts of this view in the language of the person calling it. */
    protected final IdentityTexts texts() {
        return texts;
    }

    /** Short form of {@code texts().get(key, args)}, the most frequent call of all. */
    protected final String text(String key, Object... args) {
        return texts.get(key, args);
    }

    /**
     * Stretches the view across the whole page and puts the content in the
     * middle. Intended for sign-in: it is short enough that it would otherwise
     * stick to the top.
     */
    protected final void centerOnPage() {
        setSizeFull();
        setMaxWidth("100%");
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
    }

    /**
     * Takes components in and gives them the full column width. This is the
     * entire reason the views do not scroll sideways on a phone, which is why
     * every field and every button goes through here and not through
     * {@code add(...)}.
     */
    protected final void addFullWidth(Component... components) {
        for (Component component : components) {
            add(fullWidth(component));
        }
    }

    /**
     * The same rule for components that end up somewhere other than directly in
     * the view, in the column from {@link #centeredColumn()} for example.
     */
    protected static <T extends Component> T fullWidth(T component) {
        if (component instanceof HasSize sized) {
            sized.setWidthFull();
        }
        return component;
    }

    /**
     * A column in the maximum width of the view, already added.
     *
     * <p>For the views that take up the whole page themselves
     * ({@link #centerOnPage()}): there the view is as wide as the screen, and a
     * button spanning the full width would look lost on a desktop. Everything
     * that goes in belongs through {@link #fullWidth}.
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

    /** Heading of a view. */
    protected final H2 heading(String key, Object... args) {
        return new H2(text(key, args));
    }

    /** Body text of a view. */
    protected final Paragraph paragraph(String key, Object... args) {
        return new Paragraph(text(key, args));
    }

    /** The button that completes the view; exactly one per view. */
    protected final Button primaryButton(String key, String id,
                                         ComponentEventListener<ClickEvent<Button>> listener) {
        Button button = new Button(text(key), listener);
        button.addThemeVariants(PRIMARY_VARIANT);
        button.setId(id);
        return button;
    }

    /** A side route that leaves the view without saving anything. */
    protected final Button secondaryButton(String key, String id,
                                           ComponentEventListener<ClickEvent<Button>> listener) {
        Button button = new Button(text(key), listener);
        button.addThemeVariants(TERTIARY_VARIANT);
        button.setId(id);
        return button;
    }

    /** A button leading to another route of this building block. */
    protected final Button navigationButton(String key, String route) {
        return new Button(text(key), event -> UI.getCurrent().navigate(route));
    }

    /**
     * A notification above the form. Top and centred, because the form itself
     * occupies the middle of the column; at the bottom edge the message would
     * sit behind the keyboard on a phone.
     */
    protected final void warn(String message) {
        Duration duration = ui.notificationDuration();
        Notification notification = Notification.show(message, (int) duration.toMillis(),
                Notification.Position.TOP_CENTER);
        notification.addThemeVariants(WARNING_VARIANT);
    }

    /**
     * Turns the message keys of this building block into a text.
     *
     * <p>Only the known ones: an unknown key -- from an application's own
     * service, say -- gets the generic text rather than a raw key on screen.
     */
    protected final String translate(IdentityException e) {
        String key = e.getMessageKey();
        if (IdentityMessageKeys.PASSWORD_TOO_SHORT.equals(key)) {
            return text(key, passwordMinLength);
        }
        return COMMON_KEYS.contains(key) ? text(key) : text(IdentityMessageKeys.UNEXPECTED);
    }
}

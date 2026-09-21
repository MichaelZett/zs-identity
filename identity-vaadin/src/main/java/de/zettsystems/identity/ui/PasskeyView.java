package de.zettsystems.identity.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.application.IdentityUserDetails;
import de.zettsystems.identity.application.PasskeyService;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.PasskeyDto;
import jakarta.annotation.security.PermitAll;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;

/**
 * The passkeys of the signed-in account: add one, see the ones there are,
 * remove one. An application links here from its settings.
 *
 * <p>The one view of the building block <em>inside</em> the application's
 * layout ({@code autoLayout} left on): it is reached from within the
 * application, by someone already signed in, and the way back is the
 * application's navigation. The other views are the way in and stay
 * outside.
 *
 * <p>Registering needs a fresh sign-in with the password. A session that
 * only rests on the remember-me cookie ({@code RememberMeAuthenticationToken})
 * sees the list but not the form -- the same rule the filter chain applies
 * to the registration endpoints ({@code fullyAuthenticated()}), said here
 * before the person tries. A session signed in with a passkey counts as
 * fresh: whoever just used Face ID may add another.
 *
 * <p>Managed accounts and open invitations never get here: they cannot sign
 * in, and the view is for the signed-in account only.
 */
@Route(value = IdentityRoutes.PASSKEYS)
@PermitAll
public class PasskeyView extends IdentityFormView implements BeforeEnterObserver {

    static final String REGISTER_BUTTON_ID = "passkeys-register-button";
    static final String SIGN_IN_AGAIN_BUTTON_ID = "passkeys-sign-in-again-button";
    static final String DELETE_BUTTON_ID_PREFIX = "passkeys-delete-button-";
    static final String LIST_ID = "passkeys-list";
    static final String EMPTY_ID = "passkeys-empty";

    private final PasskeyService passkeyService;
    private final AuthenticationContext authenticationContext;
    private final IdentityProperties properties;
    private final DateTimeFormatter dates;

    private final TextField label = new TextField();
    private final VerticalLayout list = new VerticalLayout();

    /**
     * @param clock the zone the dates are shown in comes from here; the
     *              building block has no better source, since a browser's
     *              zone is not known when the page is built
     */
    public PasskeyView(PasskeyService passkeyService, AuthenticationContext authenticationContext,
                       IdentityProperties properties, IdentityMessages messages, Clock clock) {
        super(messages, properties, "passkeys");
        this.passkeyService = passkeyService;
        this.authenticationContext = authenticationContext;
        this.properties = properties;
        this.dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(texts().locale())
                .withZone(clock.getZone());
        label.setLabel(text("identity.passkeys.label"));
        label.setPlaceholder(text("identity.passkeys.labelPlaceholder"));
        label.setMaxLength(PasskeyDto.LABEL_MAX_LENGTH);
        list.setPadding(false);
        list.setSpacing(false);
        list.setId(LIST_ID);
    }

    @Override
    public String getPageTitle() {
        return text("identity.passkeys.pageTitle");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        IdentityUserDetails user = currentUser();
        if (user == null) {
            // The access rule keeps this away; second line of defence.
            event.forwardTo(IdentityRoutes.LOGIN);
            return;
        }
        add(heading("identity.passkeys.title"));
        if (!properties.passkeys().enabled()) {
            add(paragraph("identity.passkeys.disabled"));
            return;
        }
        add(paragraph("identity.passkeys.intro"));
        if (isRememberMeSession()) {
            add(paragraph("identity.passkeys.freshLoginRequired"));
            Button signInAgain = navigationButton("identity.passkeys.signInAgain", IdentityRoutes.LOGIN);
            signInAgain.setId(SIGN_IN_AGAIN_BUTTON_ID);
            addFullWidth(signInAgain);
        } else {
            Button register = primaryButton("identity.passkeys.register", REGISTER_BUTTON_ID, e -> register());
            addFullWidth(label, register);
        }
        addFullWidth(list);
        refreshList(user.userId());
    }

    /** Starts the ceremony in the browser; the outcome comes back through {@link #onRegistered}. */
    private void register() {
        String name = label.isEmpty() ? text("identity.passkeys.defaultLabel") : label.getValue().strip();
        PasskeyScripts.register(this, name).then(String.class, result -> onRegistered(), this::onRegistrationError);
    }

    /** Package-visible so that the test can play the browser's answer. */
    void onRegistered() {
        IdentityUserDetails user = currentUser();
        if (user == null) {
            return;
        }
        label.clear();
        refreshList(user.userId());
        warn(text("identity.passkeys.registered"));
    }

    /** Package-visible so that the test can play the browser's answer. */
    void onRegistrationError(@Nullable String message) {
        warn(text("identity.passkeys.error." + PasskeyScripts.errorCode(message)));
    }

    /** Package-visible so that the test can drive it without the row's button. */
    void executeDelete(Long passkeyId) {
        IdentityUserDetails user = currentUser();
        if (user == null) {
            return;
        }
        try {
            passkeyService.delete(user.userId(), passkeyId);
            warn(text("identity.passkeys.deleted"));
        } catch (IdentityException e) {
            warn(translate(e));
        }
        refreshList(user.userId());
    }

    private void refreshList(Long userId) {
        list.removeAll();
        List<PasskeyDto> passkeys = passkeyService.findAllOf(userId);
        if (passkeys.isEmpty()) {
            Div empty = new Div(text("identity.passkeys.empty"));
            empty.setId(EMPTY_ID);
            list.add(empty);
            return;
        }
        passkeys.forEach(passkey -> list.add(row(passkey)));
    }

    /**
     * One passkey: label and dates on the left, the remove button on the
     * right. A row, not a grid: three columns do not fit on a phone, and a
     * handful of passkeys needs no sorting.
     */
    private HorizontalLayout row(PasskeyDto passkey) {
        Div text = new Div();
        text.addClassName(VIEW_CLASS + "__passkey");
        Span name = new Span(passkey.label());
        name.addClassName(VIEW_CLASS + "__passkey-label");
        Div dates = new Div(datesOf(passkey));
        dates.addClassName(VIEW_CLASS + "__passkey-dates");
        text.add(name, dates);

        Button remove = secondaryButton("identity.passkeys.delete", DELETE_BUTTON_ID_PREFIX + passkey.id(),
                e -> executeDelete(passkey.id()));

        HorizontalLayout row = new HorizontalLayout(text, remove);
        row.setWidthFull();
        row.setPadding(false);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setFlexGrow(1, text);
        row.addClassName(VIEW_CLASS + "__passkey-row");
        return row;
    }

    private String datesOf(PasskeyDto passkey) {
        String created = text("identity.passkeys.created", dates.format(passkey.createdAt()));
        Instant lastUsed = passkey.lastUsedAt();
        if (lastUsed == null) {
            return created;
        }
        return created + " · " + text("identity.passkeys.lastUsed", dates.format(lastUsed));
    }

    private @Nullable IdentityUserDetails currentUser() {
        return authenticationContext.getAuthenticatedUser(IdentityUserDetails.class).orElse(null);
    }

    /** Whether the session rests on the remember-me cookie alone; package-visible for the test. */
    static boolean isRememberMeSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof RememberMeAuthenticationToken;
    }
}

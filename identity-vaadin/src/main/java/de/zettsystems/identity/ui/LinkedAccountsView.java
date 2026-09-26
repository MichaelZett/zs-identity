package de.zettsystems.identity.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import de.zettsystems.identity.application.ExternalIdentityService;
import de.zettsystems.identity.application.ExternalProviders;
import de.zettsystems.identity.application.ExternalSignInException;
import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.application.IdentityUserDetails;
import de.zettsystems.identity.values.ExternalIdentityDto;
import de.zettsystems.identity.values.ExternalProvider;
import de.zettsystems.identity.values.IdentityPaths;
import de.zettsystems.identity.values.IdentityProperties;
import jakarta.annotation.security.PermitAll;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The external providers linked to the signed-in account (since 1.2.0): see
 * which there are, link another, unlink one. An application links here from
 * its settings, next to the passkeys.
 *
 * <p>Inside the application's layout, like {@link PasskeyView}, and for the
 * same reason. Linking follows the passkey rule too: a session that rests on
 * the remember-me cookie alone sees the list but not the buttons, because
 * adding a way into the account needs a fresh sign-in -- the security
 * configurer refuses it otherwise, and this view says so before anyone tries.
 *
 * <p>Linking is a round trip through the provider; the browser comes back
 * here with {@code linked=<registration id>} or with the reason it was turned
 * down in {@link IdentityPaths#EXTERNAL_ERROR_PARAMETER}.
 */
@Route(value = IdentityRoutes.LINKED_ACCOUNTS)
@PermitAll
public class LinkedAccountsView extends IdentityFormView implements BeforeEnterObserver {

    static final String LINK_BUTTON_ID_PREFIX = "linked-link-button-";
    static final String UNLINK_BUTTON_ID_PREFIX = "linked-unlink-button-";
    static final String SIGN_IN_AGAIN_BUTTON_ID = "linked-sign-in-again-button";
    static final String LIST_ID = "linked-list";
    static final String EMPTY_ID = "linked-empty";
    static final String MESSAGE_ID = "linked-message";

    private final ExternalIdentityService identityService;
    private final ExternalProviders externalProviders;
    private final AuthenticationContext authenticationContext;
    private final IdentityProperties properties;
    private final DateTimeFormatter dates;

    private final VerticalLayout list = new VerticalLayout();
    private final Div message = new Div();

    /**
     * @param clock the zone the dates are shown in; see {@link PasskeyView}
     */
    public LinkedAccountsView(ExternalIdentityService identityService, ExternalProviders externalProviders,
                              AuthenticationContext authenticationContext, IdentityProperties properties,
                              IdentityMessages messages, Clock clock) {
        super(messages, properties, "linked-accounts");
        this.identityService = identityService;
        this.externalProviders = externalProviders;
        this.authenticationContext = authenticationContext;
        this.properties = properties;
        this.dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(texts().locale())
                .withZone(clock.getZone());
        list.setPadding(false);
        list.setSpacing(false);
        list.setId(LIST_ID);
        message.setId(MESSAGE_ID);
    }

    @Override
    public String getPageTitle() {
        return text("identity.linked.pageTitle");
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
        add(heading("identity.linked.title"));
        List<ExternalProvider> providers = properties.oauth2().enabled() ? externalProviders.offered() : List.of();
        if (providers.isEmpty()) {
            add(paragraph("identity.linked.disabled"));
            return;
        }
        add(paragraph("identity.linked.intro"));
        showOutcome(event.getLocation().getQueryParameters().getParameters(), providers);
        addFullWidth(list);
        refresh(user.userId(), providers);
    }

    /** What the round trip through the provider reported, above the list. */
    private void showOutcome(Map<String, List<String>> parameters, List<ExternalProvider> providers) {
        List<String> linked = parameters.getOrDefault(IdentityPaths.LINKED_PARAMETER, List.of());
        List<String> refused = parameters.getOrDefault(IdentityPaths.EXTERNAL_ERROR_PARAMETER, List.of());
        if (!refused.isEmpty()) {
            String reason = ExternalSignInException.Reason.fromCode(refused.getFirst())
                    .orElse(ExternalSignInException.Reason.FAILED)
                    .code();
            message.setText(text("identity.login.external.error." + reason));
        } else if (!linked.isEmpty()) {
            message.setText(text("identity.linked.linked", nameOf(linked.getFirst(), providers)));
        } else {
            return;
        }
        add(message);
    }

    /** Package-visible so that the test can drive it without the row's button. */
    void executeUnlink(String registrationId) {
        IdentityUserDetails user = currentUser();
        if (user == null) {
            return;
        }
        try {
            identityService.unlink(user.userId(), registrationId);
            warn(text("identity.linked.unlinked"));
        } catch (IdentityException e) {
            warn(translate(e));
        }
        refresh(user.userId(), externalProviders.offered());
    }

    private void refresh(Long userId, List<ExternalProvider> providers) {
        list.removeAll();
        List<ExternalIdentityDto> identities = identityService.findAllOf(userId);
        if (identities.isEmpty()) {
            Div empty = new Div(text("identity.linked.empty"));
            empty.setId(EMPTY_ID);
            list.add(empty);
        } else {
            identities.forEach(identity -> list.add(row(identity, providers)));
        }

        Set<String> linked = identities.stream()
                .map(ExternalIdentityDto::registrationId)
                .collect(Collectors.toSet());
        List<ExternalProvider> linkable = providers.stream()
                .filter(provider -> !linked.contains(provider.registrationId()))
                .toList();
        if (linkable.isEmpty()) {
            return;
        }
        if (PasskeyView.isRememberMeSession()) {
            list.add(new Div(text("identity.linked.freshLoginRequired")));
            Button signInAgain = navigationButton("identity.linked.signInAgain", IdentityRoutes.LOGIN);
            signInAgain.setId(SIGN_IN_AGAIN_BUTTON_ID);
            list.add(fullWidth(signInAgain));
            return;
        }
        for (ExternalProvider provider : linkable) {
            list.add(fullWidth(providerButton("identity.linked.link",
                    LINK_BUTTON_ID_PREFIX + provider.registrationId(), provider,
                    provider.authorizationPath() + "?" + IdentityPaths.LINK_PARAMETER)));
        }
    }

    /** One provider: name, address and dates on the left, unlink on the right; see {@link PasskeyView}. */
    private HorizontalLayout row(ExternalIdentityDto identity, List<ExternalProvider> providers) {
        Div description = new Div();
        description.addClassName(VIEW_CLASS + "__linked");
        Span name = new Span(nameOf(identity.registrationId(), providers));
        name.addClassName(VIEW_CLASS + "__linked-name");
        description.add(name);
        String email = identity.email();
        if (email != null) {
            Div address = new Div(email);
            address.addClassName(VIEW_CLASS + "__linked-email");
            description.add(address);
        }
        Div when = new Div(datesOf(identity));
        when.addClassName(VIEW_CLASS + "__linked-dates");
        description.add(when);

        Button unlink = secondaryButton("identity.linked.unlink",
                UNLINK_BUTTON_ID_PREFIX + identity.registrationId(),
                e -> executeUnlink(identity.registrationId()));

        HorizontalLayout row = new HorizontalLayout(description, unlink);
        row.setWidthFull();
        row.setPadding(false);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setFlexGrow(1, description);
        row.addClassName(VIEW_CLASS + "__linked-row");
        return row;
    }

    private String datesOf(ExternalIdentityDto identity) {
        String created = text("identity.linked.linkedSince", dates.format(identity.createdAt()));
        Instant lastUsed = identity.lastUsedAt();
        if (lastUsed == null) {
            return created;
        }
        return created + " · " + text("identity.linked.lastUsed", dates.format(lastUsed));
    }

    /** The name the application gave the provider; the registration id for one it no longer offers. */
    private static String nameOf(String registrationId, List<ExternalProvider> providers) {
        return providers.stream()
                .filter(provider -> provider.registrationId().equals(registrationId))
                .map(ExternalProvider::name)
                .findFirst()
                .orElse(registrationId);
    }

    private @Nullable IdentityUserDetails currentUser() {
        return authenticationContext.getAuthenticatedUser(IdentityUserDetails.class).orElse(null);
    }
}

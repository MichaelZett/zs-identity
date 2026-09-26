package de.zettsystems.identity.application;

import de.zettsystems.identity.values.ExternalProvider;

import java.util.List;

/**
 * The external identity providers the application offers for signing in
 * (since 1.2.0), for the buttons on the sign-in page.
 *
 * <p>Empty while {@code zs.identity.oauth2.enabled} is off or the
 * application has no OAuth2 client on the classpath, so a page can always
 * ask. Otherwise the client registrations named in
 * {@code zs.identity.oauth2.registrations}, in that order, or all of them by
 * name.
 */
@FunctionalInterface
public interface ExternalProviders {

    /** The providers to offer, in the order to show them. */
    List<ExternalProvider> offered();

    /** None at all. */
    static ExternalProviders none() {
        return List::of;
    }
}

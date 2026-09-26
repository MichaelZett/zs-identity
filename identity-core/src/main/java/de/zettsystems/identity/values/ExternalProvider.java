package de.zettsystems.identity.values;

import java.util.Objects;

/**
 * An external identity provider the application offers for signing in (since
 * 1.2.0): what a sign-in page needs for a button.
 *
 * @param registrationId the client registration
 *                       ({@code spring.security.oauth2.client.registration.<id>})
 * @param name           what the button says, the registration's
 *                       {@code client-name} ("Google"); the registration id
 *                       when none is set
 */
public record ExternalProvider(String registrationId, String name) {

    public ExternalProvider {
        Objects.requireNonNull(registrationId, "registrationId");
        Objects.requireNonNull(name, "name");
    }

    /**
     * Where the browser goes to start signing in, relative like the paths
     * of the views: {@code oauth2/authorization/google}. Not a view -- the
     * request has to reach Spring Security, so a Vaadin link to it needs
     * {@code router-ignore}.
     */
    public String authorizationPath() {
        return IdentityPaths.OAUTH2_AUTHORIZATION.substring(1) + "/" + registrationId;
    }
}

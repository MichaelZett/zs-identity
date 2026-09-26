package de.zettsystems.identity.application;

import de.zettsystems.identity.values.ExternalProvider;
import de.zettsystems.identity.values.OAuth2Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The providers to offer, read from Spring's
 * {@link ClientRegistrationRepository}.
 *
 * <p>Loaded only when the OAuth2 client is on the classpath (see
 * {@code IdentityBeans#externalProviders}); every other class of the building
 * block stays free of its types. The repository is looked up on every call
 * rather than once: Spring Boot creates it from the properties, and asking
 * late spares the building block any thought about the order beans come up
 * in. It is a map lookup.
 */
final class ClientRegistrationProviders implements ExternalProviders {

    private static final Logger LOG = LoggerFactory.getLogger(ClientRegistrationProviders.class);

    private static final Comparator<ExternalProvider> BY_NAME =
            Comparator.comparing(ExternalProvider::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(ExternalProvider::registrationId);

    private final OAuth2Settings settings;
    private final BeanFactory beanFactory;
    private final Set<String> reportedMissing = ConcurrentHashMap.newKeySet();

    ClientRegistrationProviders(OAuth2Settings settings, BeanFactory beanFactory) {
        this.settings = settings;
        this.beanFactory = beanFactory;
    }

    @Override
    public List<ExternalProvider> offered() {
        ClientRegistrationRepository repository =
                beanFactory.getBeanProvider(ClientRegistrationRepository.class).getIfAvailable();
        if (repository == null) {
            return List.of();
        }
        return settings.registrations().isEmpty() ? all(repository) : named(repository);
    }

    /** The ones {@code zs.identity.oauth2.registrations} names, in that order. */
    private List<ExternalProvider> named(ClientRegistrationRepository repository) {
        List<ExternalProvider> providers = new ArrayList<>();
        for (String registrationId : settings.registrations()) {
            ClientRegistration registration = repository.findByRegistrationId(registrationId);
            if (registration != null) {
                providers.add(toProvider(registration));
            } else if (reportedMissing.add(registrationId)) {
                // A button that leads to an error page is worse than none.
                // Said once, not on every page load.
                LOG.warn("zs.identity.oauth2.registrations names {}, which is not configured", registrationId);
            }
        }
        return List.copyOf(providers);
    }

    /**
     * Every registration, by name: Spring's repository keeps them in a hash
     * map, and a page whose buttons change places between restarts would be
     * a puzzle. A repository that cannot list its registrations offers none.
     */
    private static List<ExternalProvider> all(ClientRegistrationRepository repository) {
        if (!(repository instanceof Iterable<?> registrations)) {
            return List.of();
        }
        List<ExternalProvider> providers = new ArrayList<>();
        for (Object registration : registrations) {
            if (registration instanceof ClientRegistration clientRegistration) {
                providers.add(toProvider(clientRegistration));
            }
        }
        providers.sort(BY_NAME);
        return List.copyOf(providers);
    }

    /** Spring sets the client name to the registration id when the configuration has none. */
    private static ExternalProvider toProvider(ClientRegistration registration) {
        String name = registration.getClientName();
        return new ExternalProvider(registration.getRegistrationId(),
                name.isBlank() ? registration.getRegistrationId() : name);
    }
}

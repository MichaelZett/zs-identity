package de.zettsystems.identity.configuration;

import de.zettsystems.identity.application.PasskeyAuthenticationFailureHandler;
import de.zettsystems.identity.application.PasskeyAuthenticationProvider;
import de.zettsystems.identity.values.IdentityPaths;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.PasskeySettings;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpMessageConverterAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.PublicKeyCredentialRequestOptionsFilter;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;
import org.springframework.security.web.webauthn.management.CredentialRecordOwnerAuthorizationManager;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;
import org.springframework.security.web.webauthn.registration.PublicKeyCredentialCreationOptionsFilter;
import org.springframework.security.web.webauthn.registration.WebAuthnRegistrationFilter;

import java.util.Set;

/**
 * Sign-in with passkeys for an application's security filter chain, in one
 * line:
 *
 * <pre>{@code
 * http.with(IdentityPasskeyConfigurer.passkeys(), Customizer.withDefaults());
 * }</pre>
 *
 * <p>Needs {@code spring-security-webauthn} on the classpath and does nothing
 * at all while {@code zs.identity.passkeys.enabled} is {@code false}: the
 * line stays in the application's code, the property decides per
 * environment. The relying party (domain, name, allowed origins) comes from
 * {@code zs.identity.passkeys.*}.
 *
 * <p>What it sets up, and why not Spring's own {@code http.webAuthn(..)}:
 *
 * <ul>
 *   <li>The four endpoints of Spring Security's WebAuthn filters
 *       ({@link IdentityPaths#PASSKEY_AUTHENTICATION_OPTIONS},
 *       {@link IdentityPaths#PASSKEY_LOGIN},
 *       {@link IdentityPaths#PASSKEY_REGISTRATION_OPTIONS},
 *       {@link IdentityPaths#PASSKEY_REGISTRATION}), with their access rules:
 *       signing in is open to everyone, registering needs a <em>fresh</em>
 *       sign-in -- {@code fullyAuthenticated()}, which a remember-me session
 *       does not satisfy. Whoever wants to add a passkey signs in with the
 *       password first, as they would to change it. The rules are added the
 *       moment the configurer is handed to {@code HttpSecurity}
 *       ({@link #setBuilder}), so they sit exactly where the {@code with(..)}
 *       call sits among the application's own rules: before an
 *       {@code anyRequest()} of the application's own, and anywhere in
 *       relation to a Vaadin configurer, which adds its {@code anyRequest()}
 *       last by itself.</li>
 *   <li>The sign-in filter with the pieces Spring's configurer leaves out:
 *       the application's {@code RememberMeServices}, so a passkey sign-in
 *       sets the remember-me cookie like the form login; the session strategy
 *       (session fixation); the shared {@code SecurityContextRepository}; and
 *       the shared {@code AuthenticationManager}, which publishes the
 *       {@code AuthenticationSuccessEvent} the {@code LoginRecorder} listens
 *       for. Spring's own filter gets a private manager without events.</li>
 *   <li>A provider whose principal is the account's {@code UserDetails}
 *       ({@code PasskeyAuthenticationProvider}), not the WebAuthn user entity
 *       Spring's provider leaves in the session.</li>
 *   <li>A JSON answer for both outcomes: on success {@code redirectUrl} from
 *       the saved request or the application root, on failure {@code 401}
 *       with a reason. The page is a {@code fetch}, so redirects would be
 *       the wrong shape.</li>
 *   <li>No default sign-in or registration page and no script of Spring's:
 *       the views of {@code identity-vaadin} do that.</li>
 * </ul>
 *
 * <p>The stores behind it ({@code UserCredentialRepository},
 * {@code PublicKeyCredentialUserEntityRepository}) are the beans of the
 * building block, so the passkeys land in {@code identity.auth_passkey}.
 */
public final class IdentityPasskeyConfigurer extends AbstractHttpConfigurer<IdentityPasskeyConfigurer, HttpSecurity> {

    /** Set in {@link #setBuilder} when passkeys are switched on; {@code null} is the marker for {@code configure}. */
    private @Nullable Wiring wiring;

    /** What {@code configure} needs from {@code setBuilder}, in one piece so that "switched on" is one check. */
    private record Wiring(WebAuthnRelyingPartyOperations relyingParty,
                          UserCredentialRepository credentials,
                          PublicKeyCredentialUserEntityRepository userEntities) {
    }

    private IdentityPasskeyConfigurer() {
        // through passkeys()
    }

    public static IdentityPasskeyConfigurer passkeys() {
        return new IdentityPasskeyConfigurer();
    }

    /**
     * Called by {@code http.with(..)} the moment the configurer is handed
     * over. The access rules go in here rather than in {@link #init}: rules
     * in {@code authorizeHttpRequests} take effect in the order they are
     * written, and {@code anyRequest()} has to be the last -- so the rules
     * belong where the application put the call, not to a later phase in
     * which an {@code anyRequest()} written right after it would already be
     * there.
     */
    @Override
    public void setBuilder(HttpSecurity http) {
        super.setBuilder(http);
        ApplicationContext context = http.getSharedObject(ApplicationContext.class);
        PasskeySettings settings = context.getBean(IdentityProperties.class).passkeys();
        if (!settings.enabled()) {
            return;
        }
        UserCredentialRepository credentials = context.getBean(UserCredentialRepository.class);
        PublicKeyCredentialUserEntityRepository userEntities =
                context.getBean(PublicKeyCredentialUserEntityRepository.class);
        UserDetailsService userDetailsService = context.getBean(UserDetailsService.class);

        PublicKeyCredentialRpEntity relyingPartyEntity = PublicKeyCredentialRpEntity.builder()
                .id(settings.rpId())
                .name(settings.rpName())
                .build();
        Webauthn4JRelyingPartyOperations operations = new Webauthn4JRelyingPartyOperations(userEntities, credentials,
                relyingPartyEntity, Set.copyOf(settings.allowedOrigins()));
        // On the very first registration Spring builds the user entity itself,
        // with the sign-in name as display name, and only saves it through our
        // store; the options it hands the browser would show the email
        // address on the authenticator. Reading the entity back gives the
        // account's name instead -- same handle, better label.
        operations.setCustomizeCreationOptions(options -> {
            Authentication current = getSecurityContextHolderStrategy().getContext().getAuthentication();
            PublicKeyCredentialUserEntity ours = current != null ? userEntities.findByUsername(current.getName()) : null;
            if (ours != null) {
                options.user(ours);
            }
        });
        this.wiring = new Wiring(operations, credentials, userEntities);

        http.authenticationProvider(new PasskeyAuthenticationProvider(operations, userDetailsService));
        http.authorizeHttpRequests(registry -> registry
                .requestMatchers(HttpMethod.POST, IdentityPaths.PASSKEY_AUTHENTICATION_OPTIONS,
                        IdentityPaths.PASSKEY_LOGIN).permitAll()
                .requestMatchers(IdentityPaths.PASSKEY_REGISTRATION_OPTIONS, IdentityPaths.PASSKEY_REGISTRATION,
                        IdentityPaths.PASSKEY_REGISTRATION + "/*").fullyAuthenticated());
    }

    @Override
    public void configure(HttpSecurity http) {
        Wiring wired = wiring;
        if (wired == null) {
            return;
        }
        WebAuthnRelyingPartyOperations operations = wired.relyingParty();
        UserCredentialRepository credentials = wired.credentials();
        PublicKeyCredentialUserEntityRepository userEntities = wired.userEntities();
        WebAuthnAuthenticationFilter signIn = new WebAuthnAuthenticationFilter();
        signIn.setAuthenticationManager(http.getSharedObject(AuthenticationManager.class));
        // The strategy an application may have replaced (Vaadin does, with a
        // UI-aware one); Spring's filters would otherwise keep the static
        // default they read at construction.
        signIn.setSecurityContextHolderStrategy(getSecurityContextHolderStrategy());
        signIn.setSecurityContextRepository(securityContextRepository(http));
        signIn.setAuthenticationSuccessHandler(successHandler(http));
        signIn.setAuthenticationFailureHandler(new PasskeyAuthenticationFailureHandler());
        SessionAuthenticationStrategy sessions = http.getSharedObject(SessionAuthenticationStrategy.class);
        if (sessions != null) {
            signIn.setSessionAuthenticationStrategy(sessions);
        }
        RememberMeServices rememberMe = http.getSharedObject(RememberMeServices.class);
        if (rememberMe != null) {
            signIn.setRememberMeServices(rememberMe);
        }
        http.addFilterBefore(postProcess(signIn), BasicAuthenticationFilter.class);

        WebAuthnRegistrationFilter registration = new WebAuthnRegistrationFilter(credentials, operations);
        registration.setDeleteCredentialAuthorizationManager(
                new CredentialRecordOwnerAuthorizationManager(credentials, userEntities));
        registration.setSecurityContextHolderStrategy(getSecurityContextHolderStrategy());
        // The creation options filter has no setter for the strategy (as of
        // Spring Security 7.1); it keeps the static one it read at construction.
        PublicKeyCredentialCreationOptionsFilter creationOptions = new PublicKeyCredentialCreationOptionsFilter(operations);
        PublicKeyCredentialRequestOptionsFilter requestOptions = new PublicKeyCredentialRequestOptionsFilter(operations);
        requestOptions.setSecurityContextHolderStrategy(getSecurityContextHolderStrategy());
        // All three after the AuthorizationFilter, so that the access rules
        // from init() are what decides -- Spring's configurer puts the two
        // options filters in front of it and checks "authenticated" itself,
        // which would let a remember-me session start a registration.
        http.addFilterAfter(registration, AuthorizationFilter.class);
        http.addFilterAfter(creationOptions, AuthorizationFilter.class);
        http.addFilterAfter(requestOptions, AuthorizationFilter.class);
    }

    /**
     * The repository every other authentication filter in the chain uses --
     * or, if none has asked for it yet, the default Spring would create for
     * them, shared so that they pick up the same one.
     */
    private static SecurityContextRepository securityContextRepository(HttpSecurity http) {
        SecurityContextRepository shared = http.getSharedObject(SecurityContextRepository.class);
        if (shared != null) {
            return shared;
        }
        SecurityContextRepository repository = new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
        http.setSharedObject(SecurityContextRepository.class, repository);
        return repository;
    }

    /** {@code {"authenticated":true,"redirectUrl":..}}, from the same request cache the form login uses. */
    private static HttpMessageConverterAuthenticationSuccessHandler successHandler(HttpSecurity http) {
        HttpMessageConverterAuthenticationSuccessHandler handler = new HttpMessageConverterAuthenticationSuccessHandler();
        RequestCache requestCache = http.getSharedObject(RequestCache.class);
        if (requestCache != null) {
            handler.setRequestCache(requestCache);
        }
        return handler;
    }
}

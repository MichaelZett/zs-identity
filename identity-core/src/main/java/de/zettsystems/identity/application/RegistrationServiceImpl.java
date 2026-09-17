package de.zettsystems.identity.application;


import de.zettsystems.identity.domain.AuthTokenType;
import de.zettsystems.identity.domain.Role;
import de.zettsystems.identity.domain.RoleRepository;
import de.zettsystems.identity.domain.UserAccount;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.IdentityPaths;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.UserAccountDto;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

class RegistrationServiceImpl implements RegistrationService {

    private static final Logger LOG = LoggerFactory.getLogger(RegistrationServiceImpl.class);

    private final UserAccountRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthTokenIssuer tokenIssuer;
    private final IdentityMailSender mailSender;
    private final PasswordHasher passwordHasher;
    private final IdentityProperties properties;
    private final Clock clock;

    RegistrationServiceImpl(UserAccountRepository userRepository,
                            RoleRepository roleRepository,
                            AuthTokenIssuer tokenIssuer,
                            IdentityMailSender mailSender,
                            PasswordHasher passwordHasher,
                            IdentityProperties properties,
                            Clock clock) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tokenIssuer = tokenIssuer;
        this.mailSender = mailSender;
        this.passwordHasher = passwordHasher;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public boolean isSelfRegistrationEnabled() {
        return properties.selfRegistrationEnabled();
    }

    @Override
    public boolean isEmailVerificationRequired() {
        return properties.emailVerificationRequired();
    }

    // Beide Eingaenge tragen @Transactional und rufen denselben privaten Kern:
    // Ein Aufruf auf "this" liefe am Proxy vorbei, die Transaktion muss also
    // an jedem oeffentlichen Eingang beginnen — sonst faellt das Abbilden der
    // LAZY gemappten Rollen auf das Dto in eine LazyInitializationException.
    @Override
    @Transactional
    public UserAccountDto register(String email, String rawPassword, AccountName name) {
        return doRegister(email, rawPassword, name, null);
    }

    @Override
    @Transactional
    public UserAccountDto register(String email, String rawPassword, AccountName name,
                                   @Nullable Locale locale) {
        return doRegister(email, rawPassword, name, locale);
    }

    private UserAccountDto doRegister(String email, String rawPassword, AccountName name,
                                      @Nullable Locale locale) {
        if (!properties.selfRegistrationEnabled()) {
            throw new IdentityException(IdentityMessageKeys.SELF_REGISTRATION_DISABLED,
                    "Self registration is disabled (zs.identity.self-registration-enabled=false)");
        }

        String normalized = UserAccount.normalizeEmail(email);
        if (userRepository.existsByEmail(normalized)) {
            throw new IdentityException(IdentityMessageKeys.EMAIL_ALREADY_REGISTERED,
                    "Email %s is already registered".formatted(normalized));
        }
        if (rawPassword.length() < properties.passwordMinLength()) {
            throw new IdentityException(IdentityMessageKeys.PASSWORD_TOO_SHORT,
                    "Password must be at least %d characters".formatted(properties.passwordMinLength()));
        }

        UserAccount user = new UserAccount(normalized, passwordHasher.hash(rawPassword), name, clock.instant());
        user.changeLocale(locale);
        user.grant(defaultRole());

        if (properties.emailVerificationRequired()) {
            userRepository.save(user);
            sendVerification(user);
        } else {
            // Ohne Bestätigungspflicht ist das Konto sofort nutzbar — sinnvoll
            // nur dort, wo gar kein Mailversand eingerichtet ist.
            user.activateWithoutVerification();
            userRepository.save(user);
        }
        return UserAccountMapper.toDto(user);
    }

    @Override
    @Transactional
    public UserAccountDto confirmEmail(String token) {
        // Mail-Scanner und Link-Vorschauen lösen den Einmal-Link oft vor dem
        // Menschen ein. Gehört das Token zu einem bereits bestätigten Konto,
        // ist fachlich alles erreicht — dann ist Erfolg die richtige Antwort,
        // nicht „Link abgelaufen" (Praxisfall Gruppentest 19.08.2026). Die
        // Prüfung läuft VOR dem Einlösen: ein abgelehntes redeem() markierte
        // die Transaktion sonst als rollback-only.
        Optional<UserAccountDto> alreadyVerified = tokenIssuer.peekUser(token, AuthTokenType.EMAIL_VERIFICATION)
                .filter(UserAccount::isEmailVerified)
                .map(UserAccountMapper::toDto);
        if (alreadyVerified.isPresent()) {
            return alreadyVerified.get();
        }

        UserAccount user = tokenIssuer.redeem(token, AuthTokenType.EMAIL_VERIFICATION);
        user.activateAfterEmailVerification();
        return UserAccountMapper.toDto(user);
    }

    @Override
    @Transactional
    public void resendVerification(String email) {
        Optional<UserAccount> found = userRepository.findByEmail(UserAccount.normalizeEmail(email));
        if (found.isEmpty() || found.get().isEmailVerified()) {
            // Bewusst still: Eine Rückmeldung würde verraten, ob es zu dieser
            // Adresse ein Konto gibt.
            LOG.debug("Verification resend requested for unknown or already verified address");
            return;
        }
        sendVerification(found.get());
    }

    private void sendVerification(UserAccount user) {
        String token = tokenIssuer.issue(user, AuthTokenType.EMAIL_VERIFICATION);
        String url = properties.urlFor(
                IdentityPaths.CONFIRM_EMAIL + "?" + IdentityPaths.TOKEN_PARAMETER + "=" + token);
        mailSender.sendEmailVerification(UserAccountMapper.toDto(user), url);
    }

    private Role defaultRole() {
        return roleRepository.findByCode(properties.defaultRoleCode())
                .orElseThrow(() -> new IdentityException(IdentityMessageKeys.DEFAULT_ROLE_MISSING,
                        ("Default role %s does not exist. Declare it in a RoleCatalog bean "
                                + "or change zs.identity.default-role-code.")
                                .formatted(properties.defaultRoleCode())));
    }
}

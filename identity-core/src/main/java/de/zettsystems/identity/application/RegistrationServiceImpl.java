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
import de.zettsystems.identity.values.RegistrationMode;
import de.zettsystems.identity.values.UserAccountDto;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

class RegistrationServiceImpl implements RegistrationService {

    private static final Logger LOG = LoggerFactory.getLogger(RegistrationServiceImpl.class);

    /** The length of {@code auth_user.registration_code}. */
    static final int MAX_CODE_LENGTH = 100;

    private final UserAccountRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthTokenIssuer tokenIssuer;
    private final IdentityMailSender mailSender;
    private final PasswordHasher passwordHasher;
    private final IdentityProperties properties;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final @Nullable RegistrationGate gate;

    /** Use {@link #requireGateWhereNeeded} before: in the mode {@code CODE} the gate is mandatory. */
    RegistrationServiceImpl(UserAccountRepository userRepository,
                            RoleRepository roleRepository,
                            AuthTokenIssuer tokenIssuer,
                            IdentityMailSender mailSender,
                            PasswordHasher passwordHasher,
                            IdentityProperties properties,
                            Clock clock,
                            ApplicationEventPublisher events,
                            @Nullable RegistrationGate gate) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tokenIssuer = tokenIssuer;
        this.mailSender = mailSender;
        this.passwordHasher = passwordHasher;
        this.properties = properties;
        this.clock = clock;
        this.events = events;
        this.gate = gate;
    }

    /**
     * Checked before construction rather than inside it: a constructor that
     * throws leaves a half-built object behind (SpotBugs
     * {@code CT_CONSTRUCTOR_THROW}), and the class cannot be final because of
     * the transaction proxy.
     *
     * @throws IllegalStateException in the mode {@code CODE} without a gate:
     *                               every registration would fail, and that
     *                               is better learnt at startup than from the
     *                               first person who tries
     */
    static void requireGateWhereNeeded(IdentityProperties properties, @Nullable RegistrationGate gate) {
        if (properties.registrationMode() == RegistrationMode.CODE && gate == null) {
            throw new IllegalStateException("zs.identity.registration-mode=CODE needs a RegistrationGate bean "
                    + "that decides which invitation codes are valid");
        }
    }

    @Override
    public boolean isSelfRegistrationEnabled() {
        return properties.selfRegistrationEnabled();
    }

    @Override
    public RegistrationMode registrationMode() {
        return properties.registrationMode();
    }

    @Override
    public boolean isEmailVerificationRequired() {
        return properties.emailVerificationRequired();
    }

    // All entry points carry @Transactional and call the same private core: a
    // call on "this" would bypass the proxy, so the transaction has to start at
    // every public entry point. Otherwise mapping the LAZY roles onto the DTO
    // runs into a LazyInitializationException.
    @Override
    @Transactional
    public UserAccountDto register(String email, String rawPassword, AccountName name) {
        return doRegister(email, rawPassword, name, null, null);
    }

    @Override
    @Transactional
    public UserAccountDto register(String email, String rawPassword, AccountName name,
                                   @Nullable Locale locale) {
        return doRegister(email, rawPassword, name, locale, null);
    }

    @Override
    @Transactional
    public UserAccountDto register(String email, String rawPassword, AccountName name,
                                   @Nullable Locale locale, @Nullable String code) {
        return doRegister(email, rawPassword, name, locale, code);
    }

    private UserAccountDto doRegister(String email, String rawPassword, AccountName name,
                                      @Nullable Locale locale, @Nullable String rawCode) {
        if (!properties.registrationMode().allowsRegistration()) {
            throw new IdentityException(IdentityMessageKeys.SELF_REGISTRATION_DISABLED,
                    "Self registration is disabled (zs.identity.registration-mode=CLOSED)");
        }
        // The code before the address: otherwise anybody without a code could
        // find out which addresses have an account.
        String code = admittedCode(rawCode);

        String normalized = UserAccount.normalizeEmail(email);
        Optional<UserAccount> existing = userRepository.findByEmail(normalized);
        if (existing.isPresent()) {
            // An address without a claimed account behind it is an open
            // invitation. A second account next to it would split the person
            // in two; the way in is the link in that mail.
            throw existing.get().isClaimed()
                    ? new IdentityException(IdentityMessageKeys.EMAIL_ALREADY_REGISTERED,
                            "Email %s is already registered".formatted(normalized))
                    : new IdentityException(IdentityMessageKeys.INVITATION_PENDING,
                            "Email %s has an open invitation".formatted(normalized));
        }
        if (rawPassword.length() < properties.passwordMinLength()) {
            throw new IdentityException(IdentityMessageKeys.PASSWORD_TOO_SHORT,
                    "Password must be at least %d characters".formatted(properties.passwordMinLength()));
        }

        UserAccount user = new UserAccount(normalized, passwordHasher.hash(rawPassword), name, clock.instant());
        user.changeLocale(locale);
        user.grant(defaultRole());
        user.startRegistration(code);

        if (properties.emailVerificationRequired()) {
            userRepository.save(user);
            sendVerification(user);
        } else {
            // Without a confirmation requirement the account is usable right
            // away, which makes sense only where no mail delivery is set up.
            user.activateWithoutVerification();
            userRepository.save(user);
            user.completeRegistration().ifPresent(events::publishEvent);
        }
        return UserAccountMapper.toDto(user);
    }

    /**
     * The code without surrounding blanks, once the gate has admitted it;
     * {@code null} when none was given and none is needed.
     */
    private @Nullable String admittedCode(@Nullable String rawCode) {
        String code = rawCode == null || rawCode.isBlank() ? null : rawCode.strip();
        if (code == null) {
            if (properties.registrationMode() == RegistrationMode.CODE) {
                throw new IdentityException(IdentityMessageKeys.INVITATION_CODE_REQUIRED,
                        "Registration requires an invitation code");
            }
            return null;
        }
        if (gate == null || code.length() > MAX_CODE_LENGTH || !gate.admits(code)) {
            throw new IdentityException(IdentityMessageKeys.INVITATION_CODE_INVALID,
                    "Invitation code not admitted");
        }
        return code;
    }

    @Override
    @Transactional
    public UserAccountDto confirmEmail(String token) {
        // Mail scanners and link previews often redeem the one-time link
        // before the human does. If the token belongs to an account that is
        // already confirmed, everything it was for has been achieved, so
        // success is the right answer rather than "link expired" (seen in the
        // group test on 2026-08-19). The check runs BEFORE redeeming: a
        // rejected redeem() would otherwise mark the transaction rollback-only.
        Optional<UserAccountDto> alreadyVerified = tokenIssuer.peekUser(token, AuthTokenType.EMAIL_VERIFICATION)
                .filter(UserAccount::isEmailVerified)
                .map(UserAccountMapper::toDto);
        if (alreadyVerified.isPresent()) {
            return alreadyVerified.get();
        }

        UserAccount user = tokenIssuer.redeem(token, AuthTokenType.EMAIL_VERIFICATION);
        user.activateAfterEmailVerification();
        user.completeRegistration().ifPresent(events::publishEvent);
        return UserAccountMapper.toDto(user);
    }

    @Override
    @Transactional
    public void resendVerification(String email) {
        Optional<UserAccount> found = userRepository.findByEmail(UserAccount.normalizeEmail(email));
        if (found.isEmpty() || found.get().isEmailVerified()) {
            // Deliberately silent: any feedback would reveal whether an
            // account exists for this address.
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

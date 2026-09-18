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
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

class InvitationServiceImpl implements InvitationService {

    private final UserAccountRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthTokenIssuer tokenIssuer;
    private final IdentityMailSender mailSender;
    private final PasswordHasher passwordHasher;
    private final IdentityProperties properties;
    private final Clock clock;

    InvitationServiceImpl(UserAccountRepository userRepository,
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
    @Transactional
    public UserAccountDto inviteToClaim(Long userId, String email) {
        UserAccount user = requireUser(userId);
        if (!user.isManaged()) {
            throw new IdentityException(IdentityMessageKeys.ACCOUNT_ALREADY_CLAIMED,
                    "Account %d already has an email address".formatted(userId));
        }
        String normalized = UserAccount.normalizeEmail(email);
        requireFreeAddress(normalized);

        user.assignEmail(normalized);
        return sendInvitation(user);
    }

    @Override
    @Transactional
    public void resendInvitation(Long userId) {
        UserAccount user = requireUser(userId);
        if (user.isManaged()) {
            throw new IdentityException(IdentityMessageKeys.ACCOUNT_NOT_FOUND,
                    "Account %d has no email address — invite it first".formatted(userId));
        }
        // A claimed account belongs to someone. A second invitation would then
        // be a way to take over other people's accounts; whoever forgot their
        // password uses "forgot password".
        if (user.isClaimed()) {
            throw new IdentityException(IdentityMessageKeys.ACCOUNT_ALREADY_CLAIMED,
                    "Account %d is already claimed".formatted(userId));
        }
        sendInvitation(user);
    }

    // Both entry points carry @Transactional and call the same private core.
    // The reasoning is the same as in RegistrationServiceImpl.
    @Override
    @Transactional
    public UserAccountDto inviteNewAccount(String email, AccountName name) {
        return doInviteNewAccount(email, name, null);
    }

    @Override
    @Transactional
    public UserAccountDto inviteNewAccount(String email, AccountName name, @Nullable Locale locale) {
        return doInviteNewAccount(email, name, locale);
    }

    private UserAccountDto doInviteNewAccount(String email, AccountName name, @Nullable Locale locale) {
        String normalized = UserAccount.normalizeEmail(email);
        requireFreeAddress(normalized);

        // Through the managed account: it is exactly the state an open
        // invitation describes -- address yes, password no, no way to sign in.
        // Both invitation routes therefore end in the same state.
        UserAccount user = UserAccount.managed(name, clock.instant());
        user.assignEmail(normalized);
        user.changeLocale(locale);
        user.grant(defaultRole());
        userRepository.save(user);

        return sendInvitation(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccountDto> findInvitee(String token) {
        return tokenIssuer.peekUser(token, AuthTokenType.INVITATION).map(UserAccountMapper::toDto);
    }

    @Override
    @Transactional
    public UserAccountDto claim(String token, String rawPassword) {
        return doClaim(token, rawPassword, null);
    }

    @Override
    @Transactional
    public UserAccountDto claim(String token, String rawPassword, @Nullable Locale locale) {
        return doClaim(token, rawPassword, locale);
    }

    private UserAccountDto doClaim(String token, String rawPassword, @Nullable Locale locale) {
        if (rawPassword == null || rawPassword.length() < properties.passwordMinLength()) {
            throw new IdentityException(IdentityMessageKeys.PASSWORD_TOO_SHORT,
                    "Password must be at least %d characters".formatted(properties.passwordMinLength()));
        }

        UserAccount user = tokenIssuer.redeem(token, AuthTokenType.INVITATION);
        user.claimWithPassword(passwordHasher.hash(rawPassword));
        // Only when none was chosen while inviting; see InvitationService#claim.
        if (user.getLocale() == null) {
            user.changeLocale(locale);
        }
        return UserAccountMapper.toDto(user);
    }

    /**
     * The order matters: {@code issue} voids open tokens with a
     * {@code @Modifying(clearAutomatically = true)} query and thereby clears
     * the persistence context. A {@code Dto} built afterwards would run into a
     * {@code LazyInitializationException} when reading the LAZY roles, so it is
     * created beforehand.
     */
    private UserAccountDto sendInvitation(UserAccount user) {
        UserAccountDto recipient = UserAccountMapper.toDto(user);
        String token = tokenIssuer.issue(user, AuthTokenType.INVITATION);
        String url = properties.urlFor(
                IdentityPaths.CLAIM_ACCOUNT + "?" + IdentityPaths.TOKEN_PARAMETER + "=" + token);
        mailSender.sendInvitation(recipient, url);
        return recipient;
    }

    private UserAccount requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IdentityException(IdentityMessageKeys.ACCOUNT_NOT_FOUND,
                        "No account with id %d".formatted(userId)));
    }

    /**
     * Unlike with a password reset, an error is right here: invitations are
     * issued from an administration screen, not from a form open to everyone.
     * There is nobody to hide the information from, and whoever invites should
     * learn that the address already has an account.
     */
    private void requireFreeAddress(String normalizedEmail) {
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IdentityException(IdentityMessageKeys.EMAIL_ALREADY_REGISTERED,
                    "Email %s is already registered".formatted(normalizedEmail));
        }
    }

    private Role defaultRole() {
        return roleRepository.findByCode(properties.defaultRoleCode())
                .orElseThrow(() -> new IdentityException(IdentityMessageKeys.DEFAULT_ROLE_MISSING,
                        ("Default role %s does not exist. Declare it in a RoleCatalog bean "
                                + "or change zs.identity.default-role-code.")
                                .formatted(properties.defaultRoleCode())));
    }
}

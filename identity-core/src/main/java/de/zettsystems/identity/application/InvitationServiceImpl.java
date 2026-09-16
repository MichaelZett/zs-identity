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
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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
        // Ein gesetztes Passwort heißt: Die Einladung ist eingelöst, das Konto
        // gehört jemandem. Eine zweite Einladung wäre dann ein Weg, fremde
        // Konten zu übernehmen — wer sein Passwort vergessen hat, nimmt
        // „Passwort vergessen".
        if (user.getPasswordHash() != null) {
            throw new IdentityException(IdentityMessageKeys.ACCOUNT_ALREADY_CLAIMED,
                    "Account %d is already claimed".formatted(userId));
        }
        sendInvitation(user);
    }

    @Override
    @Transactional
    public UserAccountDto inviteNewAccount(String email, AccountName name) {
        String normalized = UserAccount.normalizeEmail(email);
        requireFreeAddress(normalized);

        // Über das verwaltete Konto: Es ist genau der Zustand, den eine offene
        // Einladung beschreibt — Adresse ja, Passwort nein, kein Anmeldeweg.
        // Beide Einladungswege enden damit im selben Zustand.
        UserAccount user = UserAccount.managed(name, clock.instant());
        user.assignEmail(normalized);
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
        if (rawPassword == null || rawPassword.length() < properties.passwordMinLength()) {
            throw new IdentityException(IdentityMessageKeys.PASSWORD_TOO_SHORT,
                    "Password must be at least %d characters".formatted(properties.passwordMinLength()));
        }

        UserAccount user = tokenIssuer.redeem(token, AuthTokenType.INVITATION);
        user.claimWithPassword(passwordHasher.hash(rawPassword));
        return UserAccountMapper.toDto(user);
    }

    /**
     * Die Reihenfolge ist wichtig: {@code issue} entwertet offene Token mit
     * einer {@code @Modifying(clearAutomatically = true)}-Abfrage und löst
     * damit den Persistence Context auf. Ein danach gebautes {@code Dto} liefe
     * beim Lesen der LAZY gemappten Rollen in eine
     * {@code LazyInitializationException} — also entsteht es vorher.
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
     * Anders als beim Passwort-Reset ist ein Fehler hier richtig: Eingeladen
     * wird aus einer Verwaltung heraus, nicht aus einem offenen Formular — es
     * gibt niemanden, vor dem die Auskunft zu verbergen wäre, und der
     * Einladende soll erfahren, dass die Adresse schon ein Konto hat.
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

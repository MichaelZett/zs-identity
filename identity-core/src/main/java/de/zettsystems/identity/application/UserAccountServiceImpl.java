package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.Role;
import de.zettsystems.identity.domain.RoleRepository;
import de.zettsystems.identity.domain.UserAccount;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.UserAccountDto;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

class UserAccountServiceImpl implements UserAccountService {

    private final UserAccountRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordHasher passwordHasher;
    private final IdentityProperties properties;
    private final Clock clock;
    private final AuthenticationRefresher authenticationRefresher;

    UserAccountServiceImpl(UserAccountRepository userRepository,
                           RoleRepository roleRepository,
                           PasswordHasher passwordHasher,
                           IdentityProperties properties,
                           Clock clock,
                           AuthenticationRefresher authenticationRefresher) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordHasher = passwordHasher;
        this.properties = properties;
        this.clock = clock;
        this.authenticationRefresher = authenticationRefresher;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccountDto> findById(Long id) {
        return userRepository.findById(id).map(UserAccountMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccountDto> findByEmail(String email) {
        return userRepository.findByEmail(UserAccount.normalizeEmail(email)).map(UserAccountMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserAccountDto> findAll() {
        return userRepository.findAllByOrderByDisplayNameAsc().stream()
                .map(UserAccountMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public UserAccountDto createAccount(String email, String rawPassword, AccountName name,
                                        boolean alreadyVerified) {
        String normalized = UserAccount.normalizeEmail(email);
        if (userRepository.existsByEmail(normalized)) {
            throw new IdentityException(IdentityMessageKeys.EMAIL_ALREADY_REGISTERED,
                    "Email %s is already registered".formatted(normalized));
        }
        requireLongEnough(rawPassword);

        UserAccount user = new UserAccount(normalized, passwordHasher.hash(rawPassword), name, clock.instant());
        if (alreadyVerified) {
            user.activateAfterEmailVerification();
        }
        user.grant(defaultRole());
        return UserAccountMapper.toDto(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserAccountDto createManagedAccount(AccountName name) {
        UserAccount user = UserAccount.managed(name, clock.instant());
        user.grant(defaultRole());
        return UserAccountMapper.toDto(userRepository.save(user));
    }

    /**
     * Vergibt die Rolle und frischt die laufende Sitzung auf, falls es die
     * eigene ist — sonst gälte die neue Rolle erst nach dem nächsten Anmelden
     * (siehe {@link AuthenticationRefresher}).
     */
    @Override
    @Transactional
    public UserAccountDto grantRole(Long userId, String roleCode) {
        UserAccount user = requireUser(userId);
        user.grant(requireRole(roleCode));
        authenticationRefresher.refreshAfterCommit(user.getEmail());
        return UserAccountMapper.toDto(user);
    }

    /**
     * Nimmt die Rolle zurück und frischt die laufende Sitzung auf. Hier wiegt
     * es schwerer als beim Vergeben: Wer eine Rolle verliert, soll sie nicht
     * bis zum nächsten Anmelden weiter ausüben können.
     */
    @Override
    @Transactional
    public UserAccountDto revokeRole(Long userId, String roleCode) {
        UserAccount user = requireUser(userId);
        user.revoke(requireRole(roleCode));
        authenticationRefresher.refreshAfterCommit(user.getEmail());
        return UserAccountMapper.toDto(user);
    }

    @Override
    @Transactional
    public UserAccountDto rename(Long userId, AccountName newName) {
        UserAccount user = requireUser(userId);
        user.rename(newName);
        return UserAccountMapper.toDto(user);
    }

    @Override
    @Transactional
    public UserAccountDto setEnabled(Long userId, boolean enabled) {
        UserAccount user = requireUser(userId);
        if (enabled) {
            user.activateWithoutVerification();
        } else {
            user.disable();
        }
        return UserAccountMapper.toDto(user);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String newRawPassword) {
        requireLongEnough(newRawPassword);
        UserAccount user = requireUser(userId);
        user.changePassword(passwordHasher.hash(newRawPassword));
        // Die Sitzung trägt das Flag „muss wechseln" — nach dem Wechsel muss
        // es dort verschwinden, sonst bliebe die Person auf der Ansicht hängen.
        authenticationRefresher.refreshAfterCommit(user.getEmail());
    }

    @Override
    @Transactional
    public UserAccountDto requirePasswordChange(Long userId) {
        UserAccount user = requireUser(userId);
        user.requirePasswordChange();
        authenticationRefresher.refreshAfterCommit(user.getEmail());
        return UserAccountMapper.toDto(user);
    }

    /**
     * Tokens hängen per Fremdschlüssel mit {@code ON DELETE CASCADE} am
     * Konto, die Rollenzuordnung räumt JPA über die Beziehung ab — es bleibt
     * nichts zurück.
     */
    @Override
    @Transactional
    public void deleteAccount(Long userId) {
        UserAccount user = requireUser(userId);
        userRepository.delete(user);
    }

    private void requireLongEnough(String rawPassword) {
        if (rawPassword.length() < properties.passwordMinLength()) {
            throw new IdentityException(IdentityMessageKeys.PASSWORD_TOO_SHORT,
                    "Password must be at least %d characters".formatted(properties.passwordMinLength()));
        }
    }

    private UserAccount requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IdentityException(IdentityMessageKeys.ACCOUNT_NOT_FOUND,
                        "No account with id %d".formatted(userId)));
    }

    private Role requireRole(String roleCode) {
        return roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IdentityException(IdentityMessageKeys.DEFAULT_ROLE_MISSING,
                        "No role with code %s — is it declared in a RoleCatalog?".formatted(roleCode)));
    }

    private Role defaultRole() {
        return requireRole(properties.defaultRoleCode());
    }
}

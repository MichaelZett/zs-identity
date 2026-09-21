package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.Role;
import de.zettsystems.identity.domain.RoleRepository;
import de.zettsystems.identity.domain.UserAccount;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.values.AccountDeleted;
import de.zettsystems.identity.values.AccountLocked;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.PasswordChanged;
import de.zettsystems.identity.values.Scope;
import de.zettsystems.identity.values.UserAccountDto;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

class UserAccountServiceImpl implements UserAccountService {

    private final UserAccountRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordHasher passwordHasher;
    private final IdentityProperties properties;
    private final Clock clock;
    private final AuthenticationRefresher authenticationRefresher;
    private final ApplicationEventPublisher events;

    UserAccountServiceImpl(UserAccountRepository userRepository,
                           RoleRepository roleRepository,
                           PasswordHasher passwordHasher,
                           IdentityProperties properties,
                           Clock clock,
                           AuthenticationRefresher authenticationRefresher,
                           ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordHasher = passwordHasher;
        this.properties = properties;
        this.clock = clock;
        this.authenticationRefresher = authenticationRefresher;
        this.events = events;
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
    @Transactional(readOnly = true)
    public List<UserAccountDto> findAllById(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return userRepository.findAllWithRolesByIdIn(ids).stream()
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
     * Grants the role and refreshes the running session if it is the caller's
     * own; otherwise the new role would only apply after the next sign-in (see
     * {@link AuthenticationRefresher}).
     */
    @Override
    @Transactional
    public UserAccountDto grantRole(Long userId, String roleCode) {
        return grant(userId, roleCode, null);
    }

    @Override
    @Transactional
    public UserAccountDto grantRole(Long userId, String roleCode, Scope scope) {
        return grant(userId, roleCode, Objects.requireNonNull(scope, "scope"));
    }

    private UserAccountDto grant(Long userId, String roleCode, @Nullable Scope scope) {
        UserAccount user = requireUser(userId);
        user.grant(requireRole(roleCode), scope);
        authenticationRefresher.refreshAfterCommit(user.getEmail());
        return UserAccountMapper.toDto(user);
    }

    /**
     * Revokes the role and refreshes the running session. This weighs more than
     * granting: whoever loses a role must not be able to keep exercising it
     * until the next sign-in.
     */
    @Override
    @Transactional
    public UserAccountDto revokeRole(Long userId, String roleCode) {
        return revoke(userId, roleCode, null);
    }

    @Override
    @Transactional
    public UserAccountDto revokeRole(Long userId, String roleCode, Scope scope) {
        return revoke(userId, roleCode, Objects.requireNonNull(scope, "scope"));
    }

    /**
     * Overridden although the interface already answers it: the {@code default}
     * method there calls {@code findById} on {@code this} and would thereby
     * bypass the transaction proxy, so the LAZY assignments would run into a
     * LazyInitializationException.
     */
    @Override
    @Transactional(readOnly = true)
    public Set<String> rolesOf(Long userId, Scope scope) {
        return userRepository.findById(userId)
                .map(user -> UserAccountMapper.toDto(user).rolesIn(scope))
                .orElseGet(Set::of);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Scope> scopesOf(Long userId, String scopeType) {
        return userRepository.findById(userId)
                .map(user -> UserAccountMapper.toDto(user).scopesOf(scopeType))
                .orElseGet(Set::of);
    }

    private UserAccountDto revoke(Long userId, String roleCode, @Nullable Scope scope) {
        UserAccount user = requireUser(userId);
        user.revoke(requireRole(roleCode), scope);
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

    /**
     * Changes the stored data only, not the running session: the language is
     * not part of the principal, and the UI switches it itself anyway -- the
     * building block would not even know which view to redraw.
     */
    @Override
    @Transactional
    public UserAccountDto changeLocale(Long userId, @Nullable Locale locale) {
        UserAccount user = requireUser(userId);
        user.changeLocale(locale);
        return UserAccountMapper.toDto(user);
    }

    /**
     * Locking announces itself ({@link AccountLocked}); unlocking does not.
     * Whoever is locked out must not stay signed in somewhere through a
     * remember-me cookie, and applications keep things of their own per
     * account. There is nothing to clean up when unlocking.
     */
    @Override
    @Transactional
    public UserAccountDto setEnabled(Long userId, boolean enabled) {
        UserAccount user = requireUser(userId);
        if (enabled) {
            user.activateWithoutVerification();
        } else {
            user.disable();
            events.publishEvent(new AccountLocked(userId, user.getEmail()));
        }
        return UserAccountMapper.toDto(user);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String newRawPassword) {
        requireLongEnough(newRawPassword);
        UserAccount user = requireUser(userId);
        user.changePassword(passwordHasher.hash(newRawPassword));
        // The session carries the "must change" flag, and after the change it
        // has to disappear there too, or the person would be stuck on the view.
        authenticationRefresher.refreshAfterCommit(user.getEmail());
        events.publishEvent(new PasswordChanged(userId, user.getEmail()));
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
     * Tokens hang off the account through a foreign key with {@code ON DELETE
     * CASCADE}, and JPA clears the role assignments through the association, so
     * nothing is left behind. What an application keeps about the person is its
     * own business -- {@link AccountDeleted} tells it.
     */
    @Override
    @Transactional
    public void deleteAccount(Long userId) {
        UserAccount user = requireUser(userId);
        // Read before deleting: after the commit neither the row nor the
        // address it signed in under can be looked up any more, and that is
        // exactly what a listener needs.
        AccountDeleted deleted = new AccountDeleted(userId, user.getEmail());
        userRepository.delete(user);
        events.publishEvent(deleted);
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

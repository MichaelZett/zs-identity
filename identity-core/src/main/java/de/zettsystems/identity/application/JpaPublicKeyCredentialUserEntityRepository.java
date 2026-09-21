package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.UserAccount;
import de.zettsystems.identity.domain.UserAccountRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Security's view of "the user behind a passkey", backed by
 * {@code auth_user} instead of Spring's own {@code user_entities} table.
 *
 * <p>A WebAuthn user entity is three things: the opaque handle the
 * authenticator stores, the sign-in name and a display name. Here they are
 * the {@code passkey_user_handle} column, the email address and the display
 * name of the account -- there is no second record of the person. Spring
 * asks {@link #findByUsername} before a registration and calls {@link #save}
 * with a fresh handle only when that came back empty, so the handle is set
 * exactly once.
 */
class JpaPublicKeyCredentialUserEntityRepository implements PublicKeyCredentialUserEntityRepository {

    private final UserAccountRepository users;

    JpaPublicKeyCredentialUserEntityRepository(UserAccountRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public @Nullable PublicKeyCredentialUserEntity findById(Bytes id) {
        return users.findByPasskeyUserHandle(id.toBase64UrlString())
                .map(JpaPublicKeyCredentialUserEntityRepository::toUserEntity)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public @Nullable PublicKeyCredentialUserEntity findByUsername(String username) {
        return users.findByEmail(UserAccount.normalizeEmail(username))
                .filter(user -> user.getPasskeyUserHandle() != null)
                .map(JpaPublicKeyCredentialUserEntityRepository::toUserEntity)
                .orElse(null);
    }

    @Override
    @Transactional
    public void save(PublicKeyCredentialUserEntity userEntity) {
        UserAccount user = users.findByEmail(UserAccount.normalizeEmail(userEntity.getName()))
                .orElseThrow(() -> new IllegalStateException("No account for the passkey user " + userEntity.getName()));
        user.assignPasskeyUserHandle(userEntity.getId().toBase64UrlString());
    }

    @Override
    @Transactional
    public void delete(Bytes id) {
        users.findByPasskeyUserHandle(id.toBase64UrlString()).ifPresent(UserAccount::withdrawPasskeyUserHandle);
    }

    private static @Nullable PublicKeyCredentialUserEntity toUserEntity(UserAccount user) {
        String email = user.getEmail();
        String handle = user.getPasskeyUserHandle();
        if (email == null || handle == null) {
            return null;
        }
        return ImmutablePublicKeyCredentialUserEntity.builder()
                .id(Bytes.fromBase64(handle))
                .name(email)
                .displayName(user.getDisplayName())
                .build();
    }
}

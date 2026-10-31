package de.zettsystems.identity.application;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Sessions are compared by Spring Security, so equality has to tell them apart correctly. */
class PasskeyAuthenticationTest {

    private static final List<GrantedAuthority> ROLES = List.of(new SimpleGrantedAuthority("ROLE_USER"));
    private static final IdentityUserDetails ANNA = account(7L, "anna@example.com");

    private static IdentityUserDetails account(long id, String email) {
        return new IdentityUserDetails(id, email, "Anna Beispiel", "hash", true, false, ROLES);
    }

    @Test
    void twoTokensOfTheSameAccountAreEqual() {
        PasskeyAuthentication token = new PasskeyAuthentication(ANNA, ROLES);

        assertThat(token)
                .isEqualTo(new PasskeyAuthentication(ANNA, ROLES))
                .hasSameHashCodeAs(new PasskeyAuthentication(ANNA, ROLES));
    }

    @Test
    void anotherAccountMakesAnotherToken() {
        assertThat(new PasskeyAuthentication(ANNA, ROLES))
                .isNotEqualTo(new PasskeyAuthentication(account(8L, "ben@example.com"), ROLES));
    }

    @Test
    void aPasswordSessionOfTheSameAccountIsNotAPasskeySession() {
        AbstractAuthenticationToken passwordSession = new UsernamePasswordAuthenticationToken(ANNA, null, ROLES);

        assertThat(new PasskeyAuthentication(ANNA, ROLES)).isNotEqualTo(passwordSession);
    }
}

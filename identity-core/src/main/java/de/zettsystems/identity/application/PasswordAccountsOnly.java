package de.zettsystems.identity.application;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * The accounts the password form may check: those with a password (since
 * 1.2.0).
 *
 * <p>An account that signs in through an external provider only is turned
 * down as if its address were unknown -- the {@code DaoAuthenticationProvider}
 * then compares against its dummy hash, so the answer takes as long as for a
 * wrong password, and whoever is guessing learns nothing about which
 * addresses have an account. The exception names no address for the same
 * reason: messages end up in logs and, in a careless application, on pages.
 */
final class PasswordAccountsOnly implements UserDetailsService {

    private final UserDetailsService accounts;

    PasswordAccountsOnly(UserDetailsService accounts) {
        this.accounts = accounts;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        UserDetails user = accounts.loadUserByUsername(username);
        if (user.getPassword() == null) {
            throw new UsernameNotFoundException("The account has no password");
        }
        return user;
    }
}

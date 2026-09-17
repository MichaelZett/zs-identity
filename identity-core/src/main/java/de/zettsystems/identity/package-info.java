/**
 * Reusable authentication building block: user account, registration, sign-in,
 * password reset, roles and permissions.
 *
 * <p>This package knows nothing about the application that embeds it. Only
 * service interfaces and immutable records are visible from the outside; the
 * JPA entities never leave the module. Applications link their own objects
 * through the plain {@code userId}.
 */
@NullMarked
package de.zettsystems.identity;

import org.jspecify.annotations.NullMarked;

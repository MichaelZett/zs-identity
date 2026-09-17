/**
 * Vaadin UI of the authentication building block: sign-in, registration,
 * verification, password reset and changing the password (including the forced
 * change through {@code PasswordChangeGuard}).
 *
 * <p>For Vaadin to find the {@code @Route} classes from this library, the
 * embedding application has to list this package in
 * {@code vaadin.allowed-packages}. Without that entry the views are simply
 * unreachable, and no error is reported.
 *
 * <p>Every route here carries {@code autoLayout = false}. An embedding
 * application usually puts a {@code @Layout} with a header, navigation and a
 * sign-out button around all of its views; that frame would otherwise wrap the
 * sign-in page as well. The building block only says "no application layout"
 * and does not have to know the application for that.
 */
@NullMarked
package de.zettsystems.identity.ui;

import org.jspecify.annotations.NullMarked;

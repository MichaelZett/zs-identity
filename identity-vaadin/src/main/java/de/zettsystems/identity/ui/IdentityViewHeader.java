package de.zettsystems.identity.ui;

import com.vaadin.flow.component.Component;

/**
 * A head above the forms of this building block -- a club's logo, a title, a
 * short line of text -- supplied by the application.
 *
 * <p>Optional: provide a bean of this type and every shipped view places the
 * created component as its first element, above the form (on the sign-in page
 * inside the centred column); without the bean nothing changes. The building
 * block only gives the component the full column width and otherwise leaves
 * it alone: no colours, no sizes, no markup of its own -- what the head looks
 * like is the application's theme, which the building block cannot know.
 *
 * <p>A bean rather than a property such as {@code ui.logo-url}: the
 * application may decide per view ({@code viewName}) and, later, per tenant
 * what to show, and the building block does not have to know how images are
 * served.
 */
@FunctionalInterface
public interface IdentityViewHeader {

    /**
     * Creates the head for one view. Called once per view instance, so the
     * component must be a fresh one each time -- a Vaadin component cannot be
     * attached twice.
     *
     * @param viewName identifier of the view, the same as in its CSS class
     *                 {@code identity-view--<viewName>}: {@code login},
     *                 {@code registration}, {@code forgot-password},
     *                 {@code resend-verification}, {@code reset-password},
     *                 {@code claim-account}, {@code confirm-email},
     *                 {@code change-password}
     */
    Component create(String viewName);
}

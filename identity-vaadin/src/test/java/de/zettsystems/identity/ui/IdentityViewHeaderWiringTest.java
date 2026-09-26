package de.zettsystems.identity.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import de.zettsystems.identity.application.ExternalProviders;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.application.RegistrationService;
import de.zettsystems.identity.values.IdentityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The head reaches the view through setter injection, and the claim behind
 * that is "Vaadin creates a route through Spring's {@code createBean}". This
 * test does exactly what Vaadin's {@code SpringInstantiator} does -- no
 * Vaadin-Spring wiring, just the bean factory -- so that the mechanism is
 * proven rather than assumed.
 */
class IdentityViewHeaderWiringTest extends AbstractViewTest {

    @Test
    void springHandsTheHeadToAViewItCreates() {
        try (AnnotationConfigApplicationContext context = contextWith(true)) {
            LoginView view = context.getAutowireCapableBeanFactory().createBean(LoginView.class);

            Component column = view.getChildren().filter(VerticalLayout.class::isInstance).findFirst().orElseThrow();
            assertThat(column.getChildren().findFirst().orElseThrow().getId()).contains("club-logo");
        }
    }

    /** {@code required = false}: without the bean the view is created all the same, and looks as before. */
    @Test
    void withoutTheBeanTheViewIsCreatedUnchanged() {
        try (AnnotationConfigApplicationContext context = contextWith(false)) {
            LoginView view = context.getAutowireCapableBeanFactory().createBean(LoginView.class);

            Component column = view.getChildren().filter(VerticalLayout.class::isInstance).findFirst().orElseThrow();
            assertThat(column.getChildren().findFirst().orElseThrow().getClass().getSimpleName())
                    .isEqualTo("LoginForm");
        }
    }

    private static AnnotationConfigApplicationContext contextWith(boolean header) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(RegistrationService.class, FakeRegistrationService::new);
        context.registerBean(IdentityProperties.class, IdentityProperties::defaults);
        context.registerBean(IdentityMessages.class, IdentityMessages::resourceBundles);
        context.registerBean(ExternalProviders.class, ExternalProviders::none);
        if (header) {
            context.registerBean(IdentityViewHeader.class, () -> viewName -> {
                Div logo = new Div("Vereinslogo");
                logo.setId("club-logo");
                return logo;
            });
        }
        context.refresh();
        return context;
    }
}

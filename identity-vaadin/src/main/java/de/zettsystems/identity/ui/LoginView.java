package de.zettsystems.identity.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.application.RegistrationService;
import de.zettsystems.identity.values.IdentityProperties;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The sign-in page.
 *
 * <p>Uses Vaadin's {@code LoginForm}, because its form POSTs to {@code /login}
 * and is therefore processed directly by Spring Security's form login. That
 * means there is no sign-in code of our own that could contain mistakes.
 *
 * <p>The only view that uses {@link #centerOnPage()}: it is so short that it
 * would otherwise stick to the top edge.
 */
@Route(value = IdentityRoutes.LOGIN, autoLayout = false)
@AnonymousAllowed
public class LoginView extends IdentityFormView implements BeforeEnterObserver {

    private static final Logger LOG = LoggerFactory.getLogger(LoginView.class);

    /** Custom properties of {@code vaadin-login-form}; see {@link #alignedWithColumn}. */
    static final String FORM_WIDTH_PROPERTY = "--vaadin-login-form-width";
    static final String FORM_PADDING_PROPERTY = "--vaadin-login-form-padding";
    private static final String FULL_WIDTH = "100%";

    static final String PASSKEY_BUTTON_ID = "login-passkey-button";

    private final LoginForm loginForm = new LoginForm();

    public LoginView(RegistrationService registrationService, IdentityProperties properties,
                     IdentityMessages messages) {
        super(messages, properties, "login");
        centerOnPage();

        loginForm.setAction(IdentityRoutes.LOGIN);
        loginForm.setI18n(loginI18n(texts()));
        loginForm.setForgotPasswordButtonVisible(true);
        loginForm.addForgotPasswordListener(
                event -> getUI().ifPresent(ui -> ui.navigate(IdentityRoutes.FORGOT_PASSWORD)));

        // Sign-in fills the page; for the form and the buttons to still share
        // one limited width, they sit in a column.
        VerticalLayout column = centeredColumn();
        column.add(alignedWithColumn(loginForm));

        // Passkeys are an option per person, never a requirement: the button
        // sits next to the form, the form stays as it is.
        if (properties.passkeys().enabled()) {
            column.add(fullWidth(passkeyButton()));
        }
        // The link only appears when self-registration is switched on.
        // Otherwise it leads to a page that rejects every input.
        if (registrationService.isSelfRegistrationEnabled()) {
            column.add(fullWidth(registerButton()));
        }
        // Without a confirmation requirement there is no verification mail, so
        // that route leads nowhere and stays away.
        if (registrationService.isEmailVerificationRequired()) {
            column.add(fullWidth(resendButton()));
        }
    }

    @Override
    public String getPageTitle() {
        return text("identity.login.pageTitle");
    }

    /**
     * Makes the form's fields line up with the buttons below it.
     *
     * <p>{@code LoginForm} has no {@code HasSize}, so {@link #fullWidth} cannot
     * reach it; the width goes onto the element by hand. That alone is not
     * enough: inside its shadow DOM the web component gives its wrapper a
     * width of its own ({@code 360px} by default) and a padding all around, so
     * on a phone the fields ended up narrower than the buttons underneath --
     * three edges, two widths. Both values are public custom properties of the
     * component and are set here, on the element, rather than in a stylesheet
     * the building block does not own. Measured at 430 px: fields and buttons
     * share the same two edges afterwards.
     */
    private static LoginForm alignedWithColumn(LoginForm form) {
        form.getStyle()
                .setWidth(FULL_WIDTH)
                .set(FORM_WIDTH_PROPERTY, FULL_WIDTH)
                .set(FORM_PADDING_PROPERTY, "0");
        return form;
    }

    private Button registerButton() {
        Button button = navigationButton("identity.login.register", IdentityRoutes.REGISTER);
        button.setId("login-register-button");
        return button;
    }

    private Button resendButton() {
        Button button = navigationButton("identity.login.resendVerification", IdentityRoutes.RESEND_VERIFICATION);
        button.setId("login-resend-verification-button");
        return button;
    }

    /**
     * Starts the WebAuthn ceremony in the browser. On success the script
     * itself moves the browser to the page after sign-in, so only the
     * failures come back here.
     */
    private Button passkeyButton() {
        return secondaryButton("identity.login.passkey", PASSKEY_BUTTON_ID,
                event -> PasskeyScripts.authenticate(this).then(String.class, result -> { }, this::onPasskeyError));
    }

    /** Package-visible so that the test can play the browser's answer. */
    void onPasskeyError(@Nullable String message) {
        String code = PasskeyScripts.errorCode(message);
        if (PasskeyScripts.FAILED.equals(code)) {
            // The one outcome nobody can act on from the screen alone: the
            // text says it did not work, and what the browser actually saw --
            // the status of a turned-down endpoint -- would otherwise stay in
            // a console no one reads.
            LOG.warn("Passkey sign-in failed in the browser: {}", message);
        }
        warn(text("identity.login.passkey.error." + code));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Spring Security appends ?error after a failed sign-in.
        if (event.getLocation().getQueryParameters().getParameters().containsKey("error")) {
            loginForm.setError(true);
        }
    }

    /**
     * Package-visible and static so that the test can check the labels:
     * {@code LoginForm.getI18n()} is protected, so the object that was set
     * cannot be read back from outside.
     */
    static LoginI18n loginI18n(IdentityTexts texts) {
        LoginI18n i18n = LoginI18n.createDefault();
        LoginI18n.Form form = i18n.getForm();
        form.setTitle(texts.get("identity.login.title"));
        form.setUsername(texts.get("identity.common.email"));
        form.setPassword(texts.get("identity.common.password"));
        form.setSubmit(texts.get("identity.login.submit"));
        form.setForgotPassword(texts.get("identity.login.forgotPassword"));
        i18n.setForm(form);

        LoginI18n.ErrorMessage error = i18n.getErrorMessage();
        error.setTitle(texts.get("identity.login.error.title"));
        error.setMessage(texts.get("identity.login.error.message"));
        i18n.setErrorMessage(error);
        return i18n;
    }
}

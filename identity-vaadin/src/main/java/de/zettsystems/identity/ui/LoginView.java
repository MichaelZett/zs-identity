package de.zettsystems.identity.ui;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import de.zettsystems.identity.application.ExternalProviders;
import de.zettsystems.identity.application.ExternalSignInException;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.application.RegistrationService;
import de.zettsystems.identity.values.ExternalProvider;
import de.zettsystems.identity.values.IdentityPaths;
import de.zettsystems.identity.values.IdentityProperties;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    static final String REGISTER_BUTTON_ID = "login-register-button";
    static final String FORGOT_PASSWORD_LINK_ID = "login-forgot-password-link";
    static final String RESEND_VERIFICATION_LINK_ID = "login-resend-verification-link";
    static final String EXTERNAL_BUTTON_ID_PREFIX = "login-external-button-";
    static final String EXTERNAL_ERROR_ID = "login-external-error";

    private final LoginForm loginForm = new LoginForm();
    private final Paragraph externalError = new Paragraph();
    private final boolean passkeysEnabled;
    private final boolean passkeyButtonShown;

    /** The shape before 1.2.0, without external providers; kept for applications and tests that build the view. */
    public LoginView(RegistrationService registrationService, IdentityProperties properties,
                     IdentityMessages messages) {
        this(registrationService, properties, messages, ExternalProviders.none());
    }

    /**
     * @param externalProviders the providers to offer a button for (since
     *                          1.2.0); none while
     *                          {@code zs.identity.oauth2.enabled} is off
     */
    @Autowired
    public LoginView(RegistrationService registrationService, IdentityProperties properties,
                     IdentityMessages messages, ExternalProviders externalProviders) {
        super(messages, properties, "login");
        centerOnPage();
        this.passkeysEnabled = properties.passkeys().enabled();
        this.passkeyButtonShown = passkeysEnabled && properties.passkeys().loginButton();

        loginForm.setAction(IdentityRoutes.LOGIN);
        loginForm.setI18n(loginI18n(texts()));
        // "Forgot password?" is a way out, not a second offer: it moves from
        // inside Vaadin's form into the footer, next to the other one.
        loginForm.setForgotPasswordButtonVisible(false);

        // Sign-in fills the page; for the form and the buttons to still share
        // one limited width, they sit in a column.
        VerticalLayout column = centeredColumn();
        column.add(alignedWithColumn(loginForm));
        // Why a sign-in through a provider was turned down; the form's own
        // error box speaks of wrong passwords and would be the wrong text.
        externalError.setId(EXTERNAL_ERROR_ID);
        externalError.setVisible(false);
        column.add(fullWidth(externalError));

        // One rank per line, loudest first. Before 0.14.0 all four sat under
        // the submit button in three shapes and equally loud; on a phone that
        // read as a heap of links rather than as a page with one obvious next
        // step.
        //
        // Passkeys are an option per person, never a requirement: the button
        // sits next to the form, the form stays as it is. An application that
        // trusts the offer in the username field switches it off.
        if (passkeyButtonShown) {
            column.add(fullWidth(passkeyButton()));
        }
        // The providers next to the passkey: another way in for someone who
        // has an account, framed like one, in the order the application
        // configured. Only those that exist -- none while switched off.
        for (ExternalProvider provider : externalProviders.offered()) {
            column.add(fullWidth(providerButton("identity.login.external",
                    EXTERNAL_BUTTON_ID_PREFIX + provider.registrationId(), provider,
                    provider.authorizationPath())));
        }
        // The one thing that carries someone who has no account yet, so it
        // keeps its frame and the full width. Only when self-registration is
        // on -- otherwise it leads to a page that rejects every input.
        if (registrationService.isSelfRegistrationEnabled()) {
            column.add(fullWidth(registerButton()));
        }
        column.add(waysOut(registrationService));
    }

    /**
     * The footer: what someone needs when the normal way is blocked. Rarely
     * wanted, and then at once -- so small and quiet, but never hidden.
     *
     * <p>"Did not get the mail?" only exists where a confirmation is
     * required; without one that route leads nowhere.
     */
    private Div waysOut(RegistrationService registrationService) {
        List<Component> links = new ArrayList<>();
        links.add(footerLink("identity.login.forgotPassword", FORGOT_PASSWORD_LINK_ID,
                IdentityRoutes.FORGOT_PASSWORD));
        if (registrationService.isEmailVerificationRequired()) {
            links.add(footerLink("identity.login.resendVerification", RESEND_VERIFICATION_LINK_ID,
                    IdentityRoutes.RESEND_VERIFICATION));
        }
        return footer(links.toArray(new Component[0]));
    }

    @Override
    public String getPageTitle() {
        return text("identity.login.pageTitle");
    }

    /**
     * Starts the quiet half of the passkey sign-in: the browser offers what
     * it has for this site in the username field, and whoever wants it is one
     * touch away instead of three. The button stays -- for an older browser,
     * for one without conditional mediation, and for anyone whose passkey the
     * offer does not turn up.
     *
     * <p>Here and not in the constructor: the script talks to the form as it
     * stands in the page. A rejection lands in the same handler as the
     * button's; everything nobody acted on resolves quietly and shows
     * nothing.
     */
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        if (passkeysEnabled) {
            PasskeyScripts.authenticateConditionally(loginForm)
                    .then(String.class, result -> { }, this::onPasskeyError);
        }
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
        button.setId(REGISTER_BUTTON_ID);
        return button;
    }

    /**
     * Starts the WebAuthn ceremony in the browser. On success the script
     * itself moves the browser to the page after sign-in, so only the
     * failures come back here.
     */
    private Button passkeyButton() {
        return secondaryButton("identity.login.passkey", PASSKEY_BUTTON_ID, event -> startPasskeySignIn());
    }

    /**
     * The button wins over the offer: a conditional request that is still
     * waiting is ended first, because a browser turns down a second
     * {@code navigator.credentials.get} while one is pending.
     */
    private void startPasskeySignIn() {
        PasskeyScripts.abortConditional(loginForm);
        PasskeyScripts.authenticate(this).then(String.class, result -> { }, this::onPasskeyError);
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
        Map<String, List<String>> parameters = event.getLocation().getQueryParameters().getParameters();
        List<String> external = parameters.getOrDefault(IdentityPaths.EXTERNAL_ERROR_PARAMETER, List.of());
        if (!external.isEmpty()) {
            // A provider turned the sign-in down; the reason decides the text.
            String reason = ExternalSignInException.Reason.fromCode(external.getFirst())
                    .orElse(ExternalSignInException.Reason.FAILED)
                    .code();
            externalError.setText(text("identity.login.external.error." + reason));
            externalError.setVisible(true);
            return;
        }
        // Spring Security appends ?error after a failed sign-in.
        if (parameters.containsKey("error")) {
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

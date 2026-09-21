package de.zettsystems.identity.application;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PasskeyAuthenticationFailureHandlerTest {

    private final PasskeyAuthenticationFailureHandler handler = new PasskeyAuthenticationFailureHandler();

    @Test
    void theAnswerIsAnUnauthorizedJsonWithTheReason() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, new DisabledException("disabled"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("{\"authenticated\":false,\"reason\":\"disabled\"}");
    }

    /** Only the account's state is named; everything else is one and the same "failed". */
    @Test
    void accountStateIsToldApartFromEverythingElse() {
        assertThat(PasskeyAuthenticationFailureHandler.reasonOf(new DisabledException("x"))).isEqualTo("disabled");
        assertThat(PasskeyAuthenticationFailureHandler.reasonOf(new LockedException("x"))).isEqualTo("disabled");
        assertThat(PasskeyAuthenticationFailureHandler.reasonOf(new AccountExpiredException("x")))
                .isEqualTo("disabled");
        assertThat(PasskeyAuthenticationFailureHandler.reasonOf(new BadCredentialsException("x")))
                .isEqualTo("failed");
    }
}

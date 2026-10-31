package de.zettsystems.identity.application;

import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.RegistrationMode;
import de.zettsystems.identity.values.UserAccountDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The {@code default} methods are the contract with an application that wrote
 * its own {@link RegistrationService} against an older version. The building
 * block overrides all of them, so nothing else runs them.
 */
class RegistrationServiceDefaultsTest {

    private static final String EMAIL = "ida@example.com";
    private static final String PASSWORD = "ein-langes-passwort";
    private static final AccountName NAME = AccountName.of("Ida", "Beispiel");

    private final RegistrationService service = mock(RegistrationService.class, CALLS_REAL_METHODS);
    private final UserAccountDto registered = mock(UserAccountDto.class);

    @Test
    void theModeFollowsTheOlderSwitchAndNeverAsksForACode() {
        doReturn(true).when(service).isSelfRegistrationEnabled();
        assertThat(service.registrationMode()).isEqualTo(RegistrationMode.OPEN);

        doReturn(false).when(service).isSelfRegistrationEnabled();
        assertThat(service.registrationMode()).isEqualTo(RegistrationMode.CLOSED);
    }

    @Test
    void theLanguageIsDroppedSoThatTheConfiguredOneApplies() {
        doReturn(registered).when(service).register(EMAIL, PASSWORD, NAME);

        assertThat(service.register(EMAIL, PASSWORD, NAME, Locale.GERMAN)).isSameAs(registered);

        verify(service).register(EMAIL, PASSWORD, NAME);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void withoutACodeTheCallIsPassedOn(String code) {
        doReturn(registered).when(service).register(EMAIL, PASSWORD, NAME);

        assertThat(service.register(EMAIL, PASSWORD, NAME, Locale.GERMAN, code)).isSameAs(registered);
    }

    @Test
    void aCodeIsTurnedDownRatherThanLostSilently() {
        assertThatThrownBy(() -> service.register(EMAIL, PASSWORD, NAME, Locale.GERMAN, "ABC123"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

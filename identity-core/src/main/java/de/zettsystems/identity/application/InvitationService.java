package de.zettsystems.identity.application;

import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.UserAccountDto;

import java.util.Optional;

/**
 * Einladungen: jemanden per Mail-Link zu einem Konto führen, statt ihn sich
 * selbst registrieren zu lassen.
 *
 * <p>Deckt zwei Fälle mit einem Mechanismus ab:
 *
 * <ol>
 *   <li><strong>Verwaltetes Konto beanspruchen</strong> — die Anwendung führt
 *       eine Person längst (ohne Adresse, ohne Passwort), und die echte Person
 *       übernimmt das Konto später. Die {@code userId} bleibt dabei stabil:
 *       Alles, was die Anwendung daran gehängt hat, bleibt hängen
 *       ({@link #inviteToClaim}).</li>
 *   <li><strong>Registrierung nur auf Einladung</strong> — mit
 *       {@code zs.identity.self-registration-enabled=false} kommt niemand von
 *       allein herein; die Verwaltung lädt Adressen ein
 *       ({@link #inviteNewAccount}).</li>
 * </ol>
 *
 * <p>Wer einladen darf, entscheidet die Anwendung — der Baustein kennt keine
 * fachlichen Rollen und prüft hier nichts.
 */
public interface InvitationService {

    /**
     * Lädt eine Person ein, ein bestehendes verwaltetes Konto zu übernehmen:
     * trägt die Adresse nach und verschickt den Link.
     *
     * <p>Das Konto bleibt bis zum Einlösen ohne Passwort und damit ohne
     * Anmeldeweg. Eine erneute Einladung an dieselbe Adresse ist möglich
     * (etwa, wenn die erste im Spam landete) — sie entwertet die vorige.
     *
     * @throws IdentityException wenn es das Konto nicht gibt
     *                           ({@code ACCOUNT_NOT_FOUND}), es schon jemandem
     *                           gehört ({@code ACCOUNT_ALREADY_CLAIMED}) oder die
     *                           Adresse bereits an einem anderen Konto hängt
     *                           ({@code EMAIL_ALREADY_REGISTERED})
     */
    UserAccountDto inviteToClaim(Long userId, String email);

    /** Klarnamen-Variante: erneute Einladung an ein Konto, das die Adresse schon trägt. */
    void resendInvitation(Long userId);

    /**
     * Legt ein Konto ohne Passwort an und lädt die Adresse ein. Der Weg für
     * „Registrierung nur auf Einladung": Die Person setzt ihr Passwort selbst,
     * ein Startpasswort muss niemand übermitteln.
     *
     * @throws IdentityException wenn die Adresse schon vergeben ist
     */
    UserAccountDto inviteNewAccount(String email, AccountName name);

    /** Klarnamen-Variante von {@link #inviteNewAccount(String, AccountName)}. */
    default UserAccountDto inviteNewAccount(String email, String firstName, String lastName) {
        return inviteNewAccount(email, AccountName.of(firstName, lastName));
    }

    /**
     * Wem eine Einladung gilt — ohne sie einzulösen. Die Einlöse-Ansicht zeigt
     * damit den Namen an, damit sichtbar ist, welches Konto man übernimmt.
     *
     * @return leer, wenn das Token unbekannt ist
     */
    Optional<UserAccountDto> findInvitee(String token);

    /**
     * Löst die Einladung ein: setzt das erste Passwort, bestätigt damit die
     * Adresse und schaltet das Konto frei.
     *
     * @throws IdentityException wenn das Token unbekannt, benutzt oder
     *                           abgelaufen ist oder das Passwort zu kurz
     */
    UserAccountDto claim(String token, String rawPassword);
}

package de.zettsystems.identity.domain;

import de.zettsystems.identity.values.Scope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Eine Rolle, die einem Konto zugeteilt ist — entweder <strong>global</strong>
 * oder für einen {@link Scope} („Admin von Verein 17").
 *
 * <p>Seit V1_5 eine eigene Entity und keine {@code @ManyToMany}-Tabelle mehr:
 * Eine Zuordnung, die selbst etwas aussagt, ist keine reine Verknüpfung.
 *
 * <p>Der Bereich liegt als zwei <strong>nicht nullbare</strong> Spalten vor,
 * leer heißt global. Grund steht in V1_5: In einem eindeutigen Index gelten
 * NULL-Werte in PostgreSQL als paarweise verschieden — dieselbe globale Rolle
 * ließe sich sonst beliebig oft vergeben. Nach außen ist der Bereich trotzdem
 * {@code null}, nicht ein leerer Wert ({@link #getScope()}).
 */
@Entity
@Table(name = "auth_user_role")
@Getter
public class RoleAssignment extends AbstractAuthEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "auth_user_role_seq")
    @SequenceGenerator(name = "auth_user_role_seq", sequenceName = "auth_user_role_seq", allocationSize = 20)
    private @Nullable Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @SuppressWarnings("NullAway.Init")
    private UserAccount user;

    // EAGER wäre hier verlockend — die Rolle wird praktisch immer gebraucht —,
    // holt aber bei jeder Benutzerliste jede Rolle einzeln nach (N+1). Die
    // Lesepfade laden sie über ihren EntityGraph mit.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    @SuppressWarnings("NullAway.Init")
    private Role role;

    @Column(name = "scope_type", nullable = false, length = 32)
    private String scopeType = "";

    @Column(name = "scope_id", nullable = false, length = 64)
    private String scopeId = "";

    protected RoleAssignment() {
        // for JPA
    }

    RoleAssignment(UserAccount user, Role role, @Nullable Scope scope) {
        this.user = Objects.requireNonNull(user, "user");
        this.role = Objects.requireNonNull(role, "role");
        this.scopeType = scope == null ? "" : scope.type();
        this.scopeId = scope == null ? "" : scope.id();
    }

    /** Der Geltungsbereich, {@code null} bei einer globalen Rolle. */
    public @Nullable Scope getScope() {
        return scopeType.isEmpty() ? null : new Scope(scopeType, scopeId);
    }

    public boolean isGlobal() {
        return scopeType.isEmpty();
    }

    /** Ob diese Zuweisung genau für den gefragten Bereich gilt ({@code null} = global). */
    public boolean appliesTo(@Nullable Scope scope) {
        return Objects.equals(getScope(), scope);
    }
}

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
 * A role granted to an account, either <strong>globally</strong> or for a
 * {@link Scope} ("admin of club 17").
 *
 * <p>Since V1_5 an entity of its own rather than a {@code @ManyToMany} table:
 * a link that says something itself is not a plain link any more.
 *
 * <p>The scope is stored as two <strong>non-nullable</strong> columns, where
 * empty means global. The reason is written down in V1_5: inside a unique
 * index, PostgreSQL treats NULL values as distinct from each other, so the
 * same global role could otherwise be granted any number of times. To the
 * outside the scope is still {@code null} rather than an empty value (see
 * {@link #getScope()}).
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

    // EAGER would be tempting here, since the role is needed almost always,
    // but it fetches every role separately for every list of users (N+1). The
    // read paths pull it in through their entity graph instead.
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

    /** The scope, {@code null} for a global role. */
    public @Nullable Scope getScope() {
        return scopeType.isEmpty() ? null : new Scope(scopeType, scopeId);
    }

    public boolean isGlobal() {
        return scopeType.isEmpty();
    }

    /** Whether this assignment applies to exactly the given scope ({@code null} = global). */
    public boolean appliesTo(@Nullable Scope scope) {
        return Objects.equals(getScope(), scope);
    }
}

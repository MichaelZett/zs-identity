package de.zettsystems.identity.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Eine Rolle mit den Berechtigungen, die an ihr hängen.
 *
 * <p>Rollen sind Daten, kein Java-Enum: Welche es gibt, bestimmt die einbindende
 * Anwendung über ihren {@code RoleCatalog}. Ein fest verdrahtetes Enum wäre in
 * der zweiten Anwendung sofort falsch.
 */
@Entity
@Table(name = "auth_role")
@Getter
public class Role extends AbstractAuthEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "auth_role_seq")
    @SequenceGenerator(name = "auth_role_seq", sequenceName = "auth_role_seq", allocationSize = 20)
    private @Nullable Long id;

    @Column(nullable = false, unique = true, length = 64)
    @SuppressWarnings("NullAway.Init") // von Hibernate per Reflection befüllt
    private String code;

    @Column(name = "display_name_key", nullable = false, length = 128)
    @SuppressWarnings("NullAway.Init")
    private String displayNameKey;

    @ElementCollection
    @CollectionTable(name = "auth_role_authority", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "authority", nullable = false, length = 128)
    private Set<String> authorities = new LinkedHashSet<>();

    protected Role() {
        // for JPA
    }

    public Role(String code, String displayNameKey) {
        this.code = Objects.requireNonNull(code, "code");
        this.displayNameKey = Objects.requireNonNull(displayNameKey, "displayNameKey");
    }

    public Set<String> getAuthorities() {
        return Collections.unmodifiableSet(authorities);
    }

    /**
     * Übernimmt Anzeigename und Berechtigungen aus dem Katalog der Anwendung.
     * Wird beim Start aufgerufen, damit Änderungen am Katalog ohne Migration
     * wirksam werden.
     */
    public void syncFromCatalog(String newDisplayNameKey, Set<String> newAuthorities) {
        this.displayNameKey = Objects.requireNonNull(newDisplayNameKey, "newDisplayNameKey");
        Objects.requireNonNull(newAuthorities, "newAuthorities");
        this.authorities.clear();
        this.authorities.addAll(newAuthorities);
    }
}

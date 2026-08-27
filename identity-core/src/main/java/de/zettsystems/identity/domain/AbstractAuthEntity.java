package de.zettsystems.identity.domain;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Basis der Entities dieses Bausteins: Optimistic Locking und Gleichheit über
 * die ID.
 *
 * <p>Bewusst eine eigene Klasse und nicht die {@code AbstractBaseEntity} der
 * einbindenden Anwendung: Dieser Baustein soll in andere Projekte wandern und
 * darf deshalb nichts aus einer Anwendung kennen. Die ~40 Zeilen Doppelung sind
 * der Preis für eine Modulgrenze, die hält.
 */
@MappedSuperclass
@Getter
public abstract class AbstractAuthEntity {

    @Version
    @Setter(AccessLevel.PROTECTED)
    private long version;

    protected AbstractAuthEntity() {
        // for JPA
    }

    public abstract @Nullable Long getId();

    // EqualsGetClass: getClass()-Vergleich bewusst. Jede Entity hat ihre eigene
    // Sequence, die IDs kollidieren also über Typen hinweg.
    @SuppressWarnings("EqualsGetClass")
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractAuthEntity that = (AbstractAuthEntity) o;
        // Zwei noch nicht gespeicherte Entities (beide id == null) gelten als ungleich.
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }
}

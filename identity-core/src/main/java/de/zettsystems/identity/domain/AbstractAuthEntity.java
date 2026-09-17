package de.zettsystems.identity.domain;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Base class of the entities in this building block: optimistic locking and
 * equality by id.
 *
 * <p>Deliberately a class of its own and not the {@code AbstractBaseEntity} of
 * the embedding application: this building block is meant to travel to other
 * projects and must therefore know nothing from any application. The ~40
 * duplicated lines are the price of a module boundary that holds.
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

    /**
     * Equality by id, and by entity type -- every entity has its own sequence,
     * so ids do collide across types.
     *
     * <p>{@link Hibernate#getClass(Object)} instead of {@code getClass()}: a
     * lazily mapped association hands out a proxy, and a proxy delegates
     * {@code equals} to its target. A plain {@code getClass()} would then
     * compare {@code Role} with {@code Role$HibernateProxy} and call the very
     * same row unequal to itself.
     *
     * <p>The {@code instanceof} comes first although the type check that
     * follows is the stricter one: without it, SpotBugs sees a cast that no
     * guard protects (BC_EQUALS_METHOD_SHOULD_WORK_FOR_ALL_OBJECTS), and it is
     * right to -- {@code Hibernate.getClass(..)} accepts any object.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbstractAuthEntity that)
                || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        // Two unsaved entities (both id == null) count as unequal.
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }
}

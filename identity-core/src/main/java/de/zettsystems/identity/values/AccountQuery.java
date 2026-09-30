package de.zettsystems.identity.values;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * What {@code UserAccountService#search} looks for. Every field is optional
 * ({@code null} = no restriction) and the fields combine with AND.
 *
 * <p>Filters that live in the application's own database -- "without a group",
 * "gender not set" -- are not part of this building block. The application
 * works out the ids and hands them in through {@code userIds} or
 * {@code excludeUserIds}.
 *
 * @param text                 part of the display name, first name, last name
 *                             or email address, ignoring case. {@code %} and
 *                             {@code _} are taken literally. Blank means no
 *                             restriction.
 * @param managed              {@code true}: only managed accounts (no email,
 *                             no way to sign in); {@code false}: none of them
 * @param invitationOpen       {@code true}: only accounts with an address that
 *                             nobody has claimed yet -- the ones an
 *                             application may "invite again"; {@code false}:
 *                             everything else
 * @param awaitingConfirmation {@code true}: only accounts that are claimed but
 *                             neither enabled nor confirmed by email --
 *                             registered, waiting for the mail link;
 *                             {@code false}: everything else
 * @param roleCode             only accounts that carry this role
 *                             <strong>globally</strong>; scoped grants do not
 *                             count
 * @param userIds              only these accounts. <strong>Empty means nobody</strong>,
 *                             not everybody: an application that filtered down
 *                             to no id must not see the whole list. Only
 *                             {@code null} lifts the restriction.
 * @param excludeUserIds       never these accounts; empty or {@code null}
 *                             excludes nothing
 * @param pending              {@code true}: only accounts somebody is still
 *                             waiting for -- {@code invitationOpen}
 *                             <strong>or</strong> {@code awaitingConfirmation},
 *                             the one OR in this record; {@code false}:
 *                             everything else. With the other fields it
 *                             combines with AND like any of them. Since 1.4.0.
 */
public record AccountQuery(@Nullable String text,
                           @Nullable Boolean managed,
                           @Nullable Boolean invitationOpen,
                           @Nullable Boolean awaitingConfirmation,
                           @Nullable String roleCode,
                           @Nullable Set<Long> userIds,
                           @Nullable Set<Long> excludeUserIds,
                           @Nullable Boolean pending) {

    public AccountQuery {
        text = text == null || text.isBlank() ? null : text.strip();
        roleCode = roleCode == null || roleCode.isBlank() ? null : roleCode.strip();
        userIds = userIds == null ? null : Set.copyOf(userIds);
        excludeUserIds = excludeUserIds == null || excludeUserIds.isEmpty() ? null : Set.copyOf(excludeUserIds);
    }

    /** The shape up to 1.3.x, without {@code pending}. */
    public AccountQuery(@Nullable String text,
                        @Nullable Boolean managed,
                        @Nullable Boolean invitationOpen,
                        @Nullable Boolean awaitingConfirmation,
                        @Nullable String roleCode,
                        @Nullable Set<Long> userIds,
                        @Nullable Set<Long> excludeUserIds) {
        this(text, managed, invitationOpen, awaitingConfirmation, roleCode, userIds, excludeUserIds, null);
    }

    /** No restriction at all: every account. */
    public static AccountQuery all() {
        return new AccountQuery(null, null, null, null, null, null, null, null);
    }

    public AccountQuery withText(@Nullable String newText) {
        return new AccountQuery(newText, managed, invitationOpen, awaitingConfirmation, roleCode, userIds,
                excludeUserIds, pending);
    }

    public AccountQuery withManaged(@Nullable Boolean newManaged) {
        return new AccountQuery(text, newManaged, invitationOpen, awaitingConfirmation, roleCode, userIds,
                excludeUserIds, pending);
    }

    public AccountQuery withInvitationOpen(@Nullable Boolean newInvitationOpen) {
        return new AccountQuery(text, managed, newInvitationOpen, awaitingConfirmation, roleCode, userIds,
                excludeUserIds, pending);
    }

    public AccountQuery withAwaitingConfirmation(@Nullable Boolean newAwaitingConfirmation) {
        return new AccountQuery(text, managed, invitationOpen, newAwaitingConfirmation, roleCode, userIds,
                excludeUserIds, pending);
    }

    public AccountQuery withRoleCode(@Nullable String newRoleCode) {
        return new AccountQuery(text, managed, invitationOpen, awaitingConfirmation, newRoleCode, userIds,
                excludeUserIds, pending);
    }

    /** Only these accounts; an empty collection means nobody, see {@link #userIds()}. */
    public AccountQuery withUserIds(@Nullable Collection<Long> newUserIds) {
        return new AccountQuery(text, managed, invitationOpen, awaitingConfirmation, roleCode,
                newUserIds == null ? null : Set.copyOf(newUserIds), excludeUserIds, pending);
    }

    public AccountQuery withExcludeUserIds(@Nullable Collection<Long> newExcludeUserIds) {
        return new AccountQuery(text, managed, invitationOpen, awaitingConfirmation, roleCode, userIds,
                newExcludeUserIds == null ? null : Set.copyOf(newExcludeUserIds), pending);
    }

    /** Invitation open or confirmation outstanding, see {@link #pending()}. */
    public AccountQuery withPending(@Nullable Boolean newPending) {
        return new AccountQuery(text, managed, invitationOpen, awaitingConfirmation, roleCode, userIds,
                excludeUserIds, newPending);
    }
}

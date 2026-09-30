package de.zettsystems.identity.application;

import de.zettsystems.identity.values.AccountQuery;
import de.zettsystems.identity.values.IdentitySchema;
import de.zettsystems.identity.values.SearchSettings;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The SQL behind {@code UserAccountService#search}: the ids of one page, and
 * the number of matches.
 *
 * <p>Native SQL, because the collation has to be named in the statement (JPQL
 * has no {@code collate}) and because only the ids are wanted here: the
 * accounts of the page are loaded afterwards with their roles in one further
 * query. A fetch join over the role collection together with a page cut would
 * make Hibernate cut in memory (HHH90003004).
 *
 * <p>Everything a caller controls goes in as a bind parameter. The only text
 * that is put into the statement is the collation name, and
 * {@link SearchSettings} lets nothing but letters, digits and {@code _ . @ -}
 * through.
 */
final class AccountSearch {

    private static final String USERS = IdentitySchema.USER_TABLE + " u";
    private static final String ROLE_ASSIGNMENTS = IdentitySchema.NAME + ".auth_user_role";
    private static final String ROLES = IdentitySchema.NAME + ".auth_role";
    // '!' and not a backslash: Hibernate's parser of native statements is
    // not the place to find out how it treats a backslash in a literal.
    private static final char ESCAPE = '!';

    private static final String CLAIMED = "(u.password_hash is not null or u.external_sign_in)";
    private static final String MANAGED = "u.email is null";
    private static final String INVITATION_OPEN = "(u.email is not null and not " + CLAIMED + ")";
    private static final String AWAITING = "(" + CLAIMED + " and u.email is not null"
            + " and not u.enabled and not u.email_verified)";
    // Built from the two above and not written out again: "pending" and the
    // single filters must never come to mean different things.
    private static final String PENDING = "(" + INVITATION_OPEN + " or " + AWAITING + ")";

    private final EntityManager entityManager;
    private final SearchSettings settings;

    AccountSearch(EntityManager entityManager, SearchSettings settings) {
        this.entityManager = entityManager;
        this.settings = settings;
    }

    /** The ids of one page, in the fixed order: last name, first name, id. */
    List<Long> pageOfIds(AccountQuery query, int offset, int limit) {
        Restriction restriction = restrict(query);
        if (restriction.nobody()) {
            return List.of();
        }
        String sql = "select u.id from " + USERS + restriction.where()
                + " order by " + lowered("coalesce(u.last_name, u.display_name)")
                + ", " + lowered("coalesce(u.first_name, u.display_name)")
                + ", u.id limit :limit offset :offset";
        Query nativeQuery = entityManager.createNativeQuery(sql, Long.class);
        restriction.bindTo(nativeQuery);
        nativeQuery.setParameter("limit", limit);
        nativeQuery.setParameter("offset", offset);
        return castToLongs(nativeQuery.getResultList());
    }

    long count(AccountQuery query) {
        Restriction restriction = restrict(query);
        if (restriction.nobody()) {
            return 0;
        }
        Query nativeQuery = entityManager.createNativeQuery(
                "select count(*) from " + USERS + restriction.where(), Long.class);
        restriction.bindTo(nativeQuery);
        return ((Number) nativeQuery.getSingleResult()).longValue();
    }

    private Restriction restrict(AccountQuery query) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> parameters = new TreeMap<>();
        Set<Long> userIds = query.userIds();
        if (userIds != null) {
            if (userIds.isEmpty()) {
                return Restriction.NOBODY;
            }
            conditions.add("u.id in (:userIds)");
            parameters.put("userIds", userIds);
        }
        Set<Long> excludeUserIds = query.excludeUserIds();
        if (excludeUserIds != null) {
            conditions.add("u.id not in (:excludeUserIds)");
            parameters.put("excludeUserIds", excludeUserIds);
        }
        String text = query.text();
        if (text != null) {
            conditions.add(textCondition());
            parameters.put("text", "%" + escapeLike(text) + "%");
        }
        flag(conditions, MANAGED, query.managed());
        flag(conditions, INVITATION_OPEN, query.invitationOpen());
        flag(conditions, AWAITING, query.awaitingConfirmation());
        flag(conditions, PENDING, query.pending());
        String roleCode = query.roleCode();
        if (roleCode != null) {
            conditions.add("exists (select 1 from " + ROLE_ASSIGNMENTS + " ur join " + ROLES
                    + " r on r.id = ur.role_id where ur.user_id = u.id and r.code = :roleCode"
                    + " and ur.scope_type = '')");
            parameters.put("roleCode", roleCode);
        }
        String where = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
        return new Restriction(where, parameters, false);
    }

    private String textCondition() {
        return List.of("u.display_name", "u.first_name", "u.last_name", "u.email").stream()
                // The search text is folded by the database as well, with the
                // same collation: folded in Java it would not match what the
                // database makes of "Ä" under a collation that only knows ASCII.
                .map(column -> lowered(column) + " like " + lowered("cast(:text as text)")
                        + " escape '" + ESCAPE + "'")
                .collect(Collectors.joining(" or ", "(", ")"));
    }

    private static void flag(List<String> conditions, String condition, @Nullable Boolean wanted) {
        if (wanted != null) {
            conditions.add(wanted ? condition : "not " + condition);
        }
    }

    /** {@code lower(expression collate "name")}; without a configured collation the database decides. */
    private String lowered(String expression) {
        return "lower(" + (settings.hasCollation()
                ? expression + " collate \"" + settings.collation() + "\""
                : expression) + ")";
    }

    private static String escapeLike(String text) {
        String escape = String.valueOf(ESCAPE);
        return text.replace(escape, escape + escape)
                .replace("%", escape + "%")
                .replace("_", escape + "_");
    }

    private static List<Long> castToLongs(List<?> rows) {
        List<Long> ids = new ArrayList<>(rows.size());
        for (Object row : rows) {
            ids.add(((Number) row).longValue());
        }
        return ids;
    }

    private record Restriction(String where, Map<String, Object> parameters, boolean nobody) {

        static final Restriction NOBODY = new Restriction("", Map.of(), true);

        void bindTo(Query query) {
            parameters.forEach(query::setParameter);
        }
    }
}

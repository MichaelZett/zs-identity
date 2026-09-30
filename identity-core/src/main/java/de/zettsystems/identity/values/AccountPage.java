package de.zettsystems.identity.values;

import java.util.List;

/**
 * One page of an account search: the accounts of the page in sort order, and
 * how many accounts match altogether.
 *
 * @param items the accounts of this page, at most the requested limit
 * @param total all accounts that match the query, not only this page
 */
public record AccountPage(List<UserAccountDto> items, long total) {

    public AccountPage {
        items = List.copyOf(items);
    }

    /** Whether accounts follow behind this page, given where it started. */
    public boolean hasMore(int offset) {
        return offset + items.size() < total;
    }
}

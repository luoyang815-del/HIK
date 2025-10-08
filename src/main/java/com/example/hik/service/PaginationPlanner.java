package com.example.hik.service;

import java.util.OptionalInt;

/**
 * Utility that decides which search result position should be used for the next page.
 */
public final class PaginationPlanner {
    private PaginationPlanner() {
    }

    public static OptionalInt calculateNext(int currentPosition, int returned, int totalMatches, int pageSize) {
        if (returned <= 0) {
            return OptionalInt.empty();
        }
        int base = Math.max(currentPosition, 1);
        int next = base + returned;
        if (totalMatches > 0 && next > totalMatches) {
            return OptionalInt.empty();
        }
        if (totalMatches <= 0 && returned < Math.max(pageSize, 1)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(next);
    }
}

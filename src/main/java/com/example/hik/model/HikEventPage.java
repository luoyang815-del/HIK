package com.example.hik.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * A single page of events returned by the Hikvision API.
 */
public final class HikEventPage {
    private final List<AccessEvent> events;
    private final OptionalInt nextSearchResultPosition;
    private final int totalMatches;
    private final int numMatches;
    private final int requestedPosition;

    public HikEventPage(List<AccessEvent> events,
                        OptionalInt nextSearchResultPosition,
                        int totalMatches,
                        int numMatches,
                        int requestedPosition) {
        this.events = Collections.unmodifiableList(events == null ? Collections.emptyList() : List.copyOf(events));
        this.nextSearchResultPosition = nextSearchResultPosition == null ? OptionalInt.empty() : nextSearchResultPosition;
        this.totalMatches = Math.max(totalMatches, 0);
        int actualSize = events == null ? 0 : events.size();
        this.numMatches = Math.max(numMatches, actualSize);
        this.requestedPosition = Math.max(requestedPosition, 1);
    }

    public List<AccessEvent> getEvents() {
        return events;
    }

    public OptionalInt getNextSearchResultPosition() {
        return nextSearchResultPosition;
    }

    public int getTotalMatches() {
        return totalMatches;
    }

    public int getNumMatches() {
        return numMatches;
    }

    public int getRequestedPosition() {
        return requestedPosition;
    }

    @Override
    public String toString() {
        return "HikEventPage{" +
            "events=" + events.size() +
            ", nextSearchResultPosition=" + (nextSearchResultPosition.isPresent() ? nextSearchResultPosition.getAsInt() : "-") +
            ", totalMatches=" + totalMatches +
            ", numMatches=" + numMatches +
            ", requestedPosition=" + requestedPosition +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HikEventPage)) {
            return false;
        }
        HikEventPage that = (HikEventPage) o;
        return totalMatches == that.totalMatches
            && numMatches == that.numMatches
            && requestedPosition == that.requestedPosition
            && Objects.equals(events, that.events)
            && nextSearchResultPosition.equals(that.nextSearchResultPosition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(events, nextSearchResultPosition, totalMatches, numMatches, requestedPosition);
    }
}

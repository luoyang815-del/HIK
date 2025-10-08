package com.example.hik.model;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Immutable representation of a time range with inclusive start and exclusive end.
 */
public final class DateRange {
    private final OffsetDateTime start;
    private final OffsetDateTime end;

    public DateRange(OffsetDateTime start, OffsetDateTime end) {
        this.start = Objects.requireNonNull(start, "start");
        this.end = Objects.requireNonNull(end, "end");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End must not be before start");
        }
    }

    public OffsetDateTime getStart() {
        return start;
    }

    public OffsetDateTime getEnd() {
        return end;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DateRange)) {
            return false;
        }
        DateRange dateRange = (DateRange) o;
        return start.equals(dateRange.start) && end.equals(dateRange.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public String toString() {
        return "DateRange{" +
            "start=" + start +
            ", end=" + end +
            '}';
    }
}

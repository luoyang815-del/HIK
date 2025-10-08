package com.example.hik.util;

import com.example.hik.model.DateRange;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Splits a large date range into daily slices which are easier for the Hikvision API
 * to process.
 */
public final class DateRangePartitioner {
    private DateRangePartitioner() {
    }

    public static List<DateRange> partitionByDay(OffsetDateTime start, OffsetDateTime end) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End must not be before start");
        }
        List<DateRange> result = new ArrayList<>();
        if (start.equals(end)) {
            result.add(new DateRange(start, end));
            return result;
        }
        OffsetDateTime cursor = start;
        while (cursor.isBefore(end)) {
            OffsetDateTime startOfNextDay = cursor.toLocalDate().plusDays(1).atStartOfDay().atOffset(cursor.getOffset());
            OffsetDateTime sliceEnd = startOfNextDay.isAfter(end) ? end : startOfNextDay;
            result.add(new DateRange(cursor, sliceEnd));
            cursor = sliceEnd;
        }
        if (result.isEmpty()) {
            result.add(new DateRange(start, end));
        }
        return result;
    }
}

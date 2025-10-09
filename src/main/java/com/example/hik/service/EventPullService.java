package com.example.hik.service;

import com.example.hik.client.HikEventFetcher;
import com.example.hik.config.ApplicationConfig;
import com.example.hik.model.AccessEvent;
import com.example.hik.model.DateRange;
import com.example.hik.model.HikEventPage;
import com.example.hik.util.DateRangePartitioner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Coordinates the retrieval of events across the configured time range.
 */
public class EventPullService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventPullService.class);
    private static final int MAX_ITERATIONS = 10_000;

    private final HikEventFetcher fetcher;
    private final EventSink sink;
    private final ApplicationConfig config;

    public EventPullService(HikEventFetcher fetcher, EventSink sink, ApplicationConfig config) {
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.config = Objects.requireNonNull(config, "config");
    }

    public void pull(OffsetDateTime start, OffsetDateTime end) throws IOException {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End must not be before start");
        }
        List<DateRange> slices = DateRangePartitioner.partitionByDay(start, end);
        LOGGER.info("Processing {} slices between {} and {}", slices.size(), start, end);
        int pageSize = Math.max(config.getHikvision().getPageSize(), 1);
        for (DateRange slice : slices) {
            fetchSlice(slice, pageSize);
        }
    }

    private void fetchSlice(DateRange slice, int pageSize) throws IOException {
        LOGGER.debug("Slice {}", slice);
        int position = 1;
        int iterations = 0;
        while (true) {
            iterations++;
            if (iterations > MAX_ITERATIONS) {
                LOGGER.warn("Stopping pagination for slice {} after reaching {} iterations", slice, MAX_ITERATIONS);
                break;
            }
            HikEventPage page = fetcher.fetch(slice, position, pageSize);
            List<AccessEvent> filtered = filter(page.getEvents());
            if (!filtered.isEmpty()) {
                sink.accept(slice, filtered);
            } else {
                LOGGER.debug("No records kept for slice {} position {}", slice, position);
            }
            OptionalInt next = page.getNextSearchResultPosition();
            if (next.isEmpty()) {
                break;
            }
            int newPosition = next.getAsInt();
            if (newPosition <= position) {
                LOGGER.warn("Detected non-advancing pagination: slice {} current {} next {}", slice, position, newPosition);
                break;
            }
            position = newPosition;
        }
    }

    private List<AccessEvent> filter(List<AccessEvent> events) {
        if (events == null || events.isEmpty()) {
            return new ArrayList<>();
        }
        List<AccessEvent> kept = new ArrayList<>(events.size());
        for (AccessEvent event : events) {
            if (event == null) {
                continue;
            }
            if (config.isSkipBlankRecords() && event.isBlank()) {
                continue;
            }
            if (config.isOnlySuccess() && !event.isSuccess()) {
                continue;
            }
            kept.add(event);
        }
        return kept;
    }
}

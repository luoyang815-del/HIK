package com.example.hik.client;

import com.example.hik.model.DateRange;
import com.example.hik.model.HikEventPage;

import java.io.IOException;

/**
 * Contract used by {@link com.example.hik.service.EventPullService} to fetch events.
 */
public interface HikEventFetcher {
    HikEventPage fetch(DateRange range, int searchResultPosition, int pageSize) throws IOException;
}

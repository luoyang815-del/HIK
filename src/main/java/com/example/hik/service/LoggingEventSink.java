package com.example.hik.service;

import com.example.hik.model.AccessEvent;
import com.example.hik.model.DateRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Simple sink that logs how many events were processed for each time slice.
 */
public class LoggingEventSink implements EventSink {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingEventSink.class);

    @Override
    public void accept(DateRange slice, List<AccessEvent> events) {
        if (events == null || events.isEmpty()) {
            LOGGER.debug("No events for slice {}", slice);
            return;
        }
        LOGGER.info("{} events for slice {}", events.size(), slice);
        if (LOGGER.isDebugEnabled()) {
            for (AccessEvent event : events) {
                LOGGER.debug("Event: {}", event);
            }
        }
    }
}

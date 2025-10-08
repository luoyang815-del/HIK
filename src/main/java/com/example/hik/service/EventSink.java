package com.example.hik.service;

import com.example.hik.model.AccessEvent;
import com.example.hik.model.DateRange;

import java.util.List;

/**
 * Target for the processed events. Implementations can persist them to a database or
 * simply log them.
 */
public interface EventSink {
    void accept(DateRange slice, List<AccessEvent> events);
}

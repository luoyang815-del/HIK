package com.example.hik.client;

import com.example.hik.model.AccessEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Parses the JSON response returned by the Hikvision API.
 */
public class HikEventParser {
    private static final DateTimeFormatter[] DATE_FORMATS = new DateTimeFormatter[] {
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_ZONED_DATE_TIME
    };

    private final ObjectMapper mapper = new ObjectMapper();

    public ParsedPage parse(String json) throws IOException {
        if (json == null || json.trim().isEmpty()) {
            return new ParsedPage(new ArrayList<>(), 0, 0, OptionalInt.empty());
        }
        JsonNode root = mapper.readTree(json);
        JsonNode searchResult = extractSearchResult(root);
        JsonNode eventsNode = findEventsNode(searchResult);

        List<AccessEvent> events = new ArrayList<>();
        if (eventsNode != null) {
            if (eventsNode.isArray()) {
                for (JsonNode node : eventsNode) {
                    events.add(toEvent(node));
                }
            } else if (!eventsNode.isMissingNode() && eventsNode.isObject()) {
                events.add(toEvent(eventsNode));
            }
        }

        int totalMatches = getInt(searchResult, "totalMatches", -1);
        int numMatches = getInt(searchResult, "numOfMatches", events.size());
        OptionalInt position = optionalInt(searchResult, "searchResultPosition");
        return new ParsedPage(events, totalMatches, numMatches, position);
    }

    private JsonNode extractSearchResult(JsonNode root) {
        if (root == null || root instanceof MissingNode) {
            return MissingNode.getInstance();
        }
        JsonNode searchResult = root.get("SearchResult");
        if (searchResult != null && !searchResult.isMissingNode()) {
            return searchResult;
        }
        return root;
    }

    private JsonNode findEventsNode(JsonNode searchResult) {
        if (searchResult == null || searchResult.isMissingNode()) {
            return MissingNode.getInstance();
        }
        JsonNode events = searchResult.get("AcsEvent");
        if (events == null || events.isMissingNode()) {
            events = searchResult.get("acsEvent");
        }
        return events == null ? MissingNode.getInstance() : events;
    }

    private AccessEvent toEvent(JsonNode node) {
        Map<String, String> attributes = new LinkedHashMap<>();
        node.fieldNames().forEachRemaining(field -> {
            JsonNode value = node.get(field);
            if (value.isValueNode()) {
                attributes.put(field, value.asText());
            } else {
                attributes.put(field, value.toString());
            }
        });
        OffsetDateTime time = parseTime(firstNonBlank(attributes,
            "eventTime", "time", "occurTime", "startTime", "captureTime"));
        return new AccessEvent(time, attributes);
    }

    private OffsetDateTime parseTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String text = value.trim();
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return OffsetDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignore) {
                // Try next formatter
            }
        }
        try {
            return OffsetDateTime.parse(text);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String firstNonBlank(Map<String, String> attributes, String... keys) {
        for (String key : keys) {
            String value = attributes.get(key);
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private int getInt(JsonNode node, String field, int fallback) {
        if (node == null || node.isMissingNode()) {
            return fallback;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isMissingNode()) {
            return fallback;
        }
        if (value.canConvertToInt()) {
            return value.intValue();
        }
        try {
            return Integer.parseInt(value.asText());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private OptionalInt optionalInt(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) {
            return OptionalInt.empty();
        }
        JsonNode value = node.get(field);
        if (value == null || value.isMissingNode()) {
            return OptionalInt.empty();
        }
        if (value.canConvertToInt()) {
            return OptionalInt.of(value.intValue());
        }
        try {
            return OptionalInt.of(Integer.parseInt(value.asText()));
        } catch (NumberFormatException ex) {
            return OptionalInt.empty();
        }
    }

    public static final class ParsedPage {
        private final List<AccessEvent> events;
        private final int totalMatches;
        private final int numMatches;
        private final OptionalInt searchResultPosition;

        ParsedPage(List<AccessEvent> events, int totalMatches, int numMatches, OptionalInt searchResultPosition) {
            this.events = events;
            this.totalMatches = totalMatches;
            this.numMatches = numMatches;
            this.searchResultPosition = searchResultPosition;
        }

        public List<AccessEvent> getEvents() {
            return events;
        }

        public int getTotalMatches() {
            return totalMatches;
        }

        public int getNumMatches() {
            return numMatches;
        }

        public OptionalInt getSearchResultPosition() {
            return searchResultPosition;
        }
    }
}

package com.example.hik.model;

import com.example.hik.service.EventFilter;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Representation of a single event coming from the Hikvision access control endpoint.
 */
public final class AccessEvent {
    private final OffsetDateTime eventTime;
    private final Map<String, String> attributes;
    private final String cardNumber;
    private final String personId;
    private final String doorName;
    private final boolean success;
    private final boolean blank;

    public AccessEvent(OffsetDateTime eventTime, Map<String, String> attributes) {
        this.eventTime = eventTime;
        Map<String, String> copy = new LinkedHashMap<>();
        if (attributes != null) {
            copy.putAll(attributes);
        }
        this.attributes = Collections.unmodifiableMap(copy);
        this.cardNumber = firstNonBlank("cardNo", "cardNumber", "card");
        this.personId = firstNonBlank("personId", "personID", "employeeNoString", "employeeNo");
        this.doorName = firstNonBlank("doorName", "readerName", "door", "doorDescription");
        this.success = EventFilter.isSuccess(this.attributes);
        this.blank = EventFilter.isBlank(this.attributes);
    }

    public OffsetDateTime getEventTime() {
        return eventTime;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public Optional<String> getCardNumber() {
        return Optional.ofNullable(cardNumber).filter(s -> !s.isBlank());
    }

    public Optional<String> getPersonId() {
        return Optional.ofNullable(personId).filter(s -> !s.isBlank());
    }

    public Optional<String> getDoorName() {
        return Optional.ofNullable(doorName).filter(s -> !s.isBlank());
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isBlank() {
        return blank;
    }

    private String firstNonBlank(String... keys) {
        for (String key : keys) {
            String value = attributes.get(key);
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AccessEvent)) {
            return false;
        }
        AccessEvent that = (AccessEvent) o;
        return success == that.success
            && blank == that.blank
            && Objects.equals(eventTime, that.eventTime)
            && Objects.equals(attributes, that.attributes)
            && Objects.equals(cardNumber, that.cardNumber)
            && Objects.equals(personId, that.personId)
            && Objects.equals(doorName, that.doorName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventTime, attributes, cardNumber, personId, doorName, success, blank);
    }

    @Override
    public String toString() {
        return "AccessEvent{" +
            "eventTime=" + eventTime +
            ", cardNumber='" + cardNumber + '\'' +
            ", personId='" + personId + '\'' +
            ", doorName='" + doorName + '\'' +
            ", success=" + success +
            ", blank=" + blank +
            '}';
    }
}

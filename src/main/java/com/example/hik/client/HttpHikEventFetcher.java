package com.example.hik.client;

import com.example.hik.config.HikvisionConfig;
import com.example.hik.model.DateRange;
import com.example.hik.model.HikEventPage;
import com.example.hik.service.PaginationPlanner;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.codec.binary.Base64;
import org.apache.hc.client5.http.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * HTTP based implementation of {@link HikEventFetcher}.
 */
public class HttpHikEventFetcher implements HikEventFetcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpHikEventFetcher.class);

    private final HikvisionConfig config;
    private final CloseableHttpClient httpClient;
    private final HikEventParser parser;
    private final ObjectMapper objectMapper;

    public HttpHikEventFetcher(HikvisionConfig config, CloseableHttpClient httpClient, HikEventParser parser) {
        this.config = config;
        this.httpClient = httpClient;
        this.parser = parser;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public HikEventPage fetch(DateRange range, int searchResultPosition, int pageSize) throws IOException {
        URI uri = resolveUri();
        HttpPost request = new HttpPost(uri);
        request.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
        request.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
        applyAuthentication(request);
        RequestBody body = new RequestBody(range.getStart(), range.getEnd(), searchResultPosition, pageSize, config.getExtraParameters());
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(body), ContentType.APPLICATION_JSON));

        LOGGER.debug("Fetching events: {} position={} size={}", range, searchResultPosition, pageSize);

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int status = response.getCode();
            String payload = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
            if (status >= 400) {
                throw new IOException("Unexpected HTTP status " + status + " body=" + payload);
            }
            HikEventParser.ParsedPage parsed = parser.parse(payload);
            int totalMatches = parsed.getTotalMatches();
            int numMatches = parsed.getNumMatches();
            OptionalInt next = PaginationPlanner.calculateNext(
                parsed.getSearchResultPosition().orElse(searchResultPosition),
                numMatches,
                totalMatches,
                pageSize
            );
            return new HikEventPage(parsed.getEvents(), next, totalMatches, numMatches, searchResultPosition);
        }
    }

    private URI resolveUri() {
        String base = config.getBaseUrl();
        if (base == null) {
            throw new IllegalStateException("hikvision.baseUrl must be provided");
        }
        String path = config.getEventsPath();
        if (path == null || path.isEmpty()) {
            path = "/ISAPI/AccessControl/AcsEvent?format=json";
        }
        if (base.endsWith("/") && path.startsWith("/")) {
            return URI.create(base.substring(0, base.length() - 1) + path);
        }
        if (!base.endsWith("/") && !path.startsWith("/")) {
            return URI.create(base + '/' + path);
        }
        return URI.create(base + path);
    }

    private void applyAuthentication(HttpUriRequestBase request) {
        if (config.getUsername() == null || config.getPassword() == null) {
            return;
        }
        String credentials = config.getUsername() + ':' + config.getPassword();
        String encoded = Base64.encodeBase64String(credentials.getBytes(StandardCharsets.UTF_8));
        request.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static final class RequestBody {
        @JsonProperty("AcsEventSearchDescription")
        private final Description description;

        RequestBody(OffsetDateTime start, OffsetDateTime end, int position, int pageSize, Map<String, String> extras) {
            this.description = new Description(start, end, position, pageSize, extras);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static final class Description {
        @JsonProperty("timeSpan")
        private final TimeSpan timeSpan;
        @JsonProperty("searchResultPosition")
        private final Integer searchResultPosition;
        @JsonProperty("maxResults")
        private final Integer maxResults;
        @JsonProperty("AcsEventFilter")
        private final Map<String, String> extra;

        Description(OffsetDateTime start, OffsetDateTime end, int position, int pageSize, Map<String, String> extras) {
            this.timeSpan = new TimeSpan(start, end);
            this.searchResultPosition = position;
            this.maxResults = pageSize;
            if (extras == null || extras.isEmpty()) {
                this.extra = null;
            } else {
                this.extra = new LinkedHashMap<>(extras);
            }
        }
    }

    private static final class TimeSpan {
        @JsonProperty("startTime")
        private final String start;
        @JsonProperty("endTime")
        private final String end;

        TimeSpan(OffsetDateTime start, OffsetDateTime end) {
            this.start = start == null ? null : start.toString();
            this.end = end == null ? null : end.toString();
        }
    }
}

package com.example.hik.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration of the Hikvision endpoint that exposes the access control events.
 */
public class HikvisionConfig {
    private String baseUrl;
    private String username;
    private String password;
    private boolean insecureTls = true;
    private String eventsPath = "/ISAPI/AccessControl/AcsEvent?format=json";
    private int pageSize = 50;
    private Duration requestTimeout = Duration.ofSeconds(30);
    private Map<String, String> extraParameters = new LinkedHashMap<>();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isInsecureTls() {
        return insecureTls;
    }

    public void setInsecureTls(boolean insecureTls) {
        this.insecureTls = insecureTls;
    }

    public String getEventsPath() {
        return eventsPath;
    }

    public void setEventsPath(String eventsPath) {
        this.eventsPath = eventsPath;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public Map<String, String> getExtraParameters() {
        if (extraParameters == null) {
            extraParameters = new LinkedHashMap<>();
        }
        return extraParameters;
    }

    public void setExtraParameters(Map<String, String> extraParameters) {
        this.extraParameters = extraParameters;
    }

    public void applyDefaults() {
        if (pageSize <= 0) {
            pageSize = 50;
        }
        if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
            requestTimeout = Duration.ofSeconds(30);
        }
        getExtraParameters();
    }

    @Override
    public String toString() {
        return "HikvisionConfig{" +
            "baseUrl='" + baseUrl + '\'' +
            ", username='" + username + '\'' +
            ", insecureTls=" + insecureTls +
            ", eventsPath='" + eventsPath + '\'' +
            ", pageSize=" + pageSize +
            ", requestTimeout=" + requestTimeout +
            ", extraParameters=" + getExtraParameters() +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HikvisionConfig)) {
            return false;
        }
        HikvisionConfig that = (HikvisionConfig) o;
        return insecureTls == that.insecureTls
            && pageSize == that.pageSize
            && Objects.equals(baseUrl, that.baseUrl)
            && Objects.equals(username, that.username)
            && Objects.equals(password, that.password)
            && Objects.equals(eventsPath, that.eventsPath)
            && Objects.equals(requestTimeout, that.requestTimeout)
            && Objects.equals(getExtraParameters(), that.getExtraParameters());
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseUrl, username, password, insecureTls, eventsPath, pageSize, requestTimeout, getExtraParameters());
    }
}

package com.example.hik.exchange;

import com.example.hik.util.AESUtil;
import com.example.hik.util.AuthUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ExchangeClient {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String baseUrl;
    private final String pathPerson;
    private final String aesKey;

    private final String authType; // basic | login
    private final String username;
    private final String password;

    private final String loginPath;
    private final String loginContentType;
    private final String loginBodyTemplate;
    private final String tokenField;
    private final String tokenHeaderName;
    private final String tokenHeaderFormat;

    public ExchangeClient(
            String baseUrl, String pathPerson, String aesKey,
            String authType, String username, String password,
            String loginPath, String loginContentType,
            String loginBodyTemplate, String tokenField, String tokenHeaderName, String tokenHeaderFormat
    ) {
        this.baseUrl = baseUrl;
        this.pathPerson = pathPerson;
        this.aesKey = aesKey;

        this.authType = authType == null ? "basic" : authType;
        this.username = username;
        this.password = password;

        this.loginPath = loginPath;
        this.loginContentType = loginContentType;
        this.loginBodyTemplate = loginBodyTemplate;
        this.tokenField = tokenField;
        this.tokenHeaderName = tokenHeaderName;
        this.tokenHeaderFormat = tokenHeaderFormat;
    }

    /** Upload "企业人流门" records. rows is a List<Map> as a JSON array that will be AES-encrypted and put into {"datas": "<base64>"} */
    public String uploadPersonCrossRecords(List<Map<String, Object>> rows) throws Exception {
        String plainArray = JSON.writeValueAsString(rows);
        String cipher = AESUtil.encrypt(aesKey, plainArray);
        String body = JSON.writeValueAsString(Map.of("datas", cipher));

        String url = baseUrl + pathPerson;
        HttpPost post = new HttpPost(url);
        post.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
        post.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());

        if ("basic".equalsIgnoreCase(authType)) {
            String basic = AuthUtil.buildBasicAuth(Objects.toString(username, ""), Objects.toString(password, ""));
            post.setHeader("Authorization", basic);
        } else if ("login".equalsIgnoreCase(authType)) {
            String token = doLoginAndGetToken();
            String hv = tokenHeaderFormat == null ? token : String.format(tokenHeaderFormat, token);
            post.setHeader(tokenHeaderName == null ? "Authorization" : tokenHeaderName, hv);
        }

        post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8)));

        try (CloseableHttpClient http = HttpClients.createDefault()) {
            return http.execute(post, resp -> new String(resp.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private String doLoginAndGetToken() throws Exception {
        if (loginPath == null) throw new IllegalStateException("loginPath not configured");
        String url = baseUrl + loginPath;
        HttpUriRequestBase req = new HttpPost(url);
        String loginBody = (loginBodyTemplate == null || loginBodyTemplate.isEmpty())
                ? JSON.writeValueAsString(Map.of("username", username, "password", password))
                : loginBodyTemplate.replace("${username}", username).replace("${password}", password);
        req.setHeader(HttpHeaders.CONTENT_TYPE, loginContentType == null ? "application/json" : loginContentType);
        req.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
        ((HttpPost) req).setEntity(new StringEntity(loginBody, ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8)));

        try (CloseableHttpClient http = HttpClients.createDefault()) {
            String resp = http.execute(req, r -> new String(r.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8));
            JsonNode node = JSON.readTree(resp);
            String field = tokenField == null ? "token" : tokenField;
            JsonNode tk = node.get(field);
            if (tk == null || tk.isNull() || tk.asText().isEmpty()) {
                throw new IllegalStateException("Login success but token not found in field: " + field + ", resp=" + resp);
            }
            return tk.asText();
        }
    }
}

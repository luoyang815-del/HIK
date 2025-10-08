package com.example.hik;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 最小改造版 Sinks：
 * - 支持 write(NormalizedEvent) 与 write(List<NormalizedEvent>)
 * - JDBC Sink：优先使用配置 insertSql；否则用默认表结构自动生成 SQL
 * - HTTP Sink：按配置 endpointBase/tableName 发送 JSON（批量）
 */
public class Sinks {

    private final Config cfg;
    private final ObjectMapper JSON = new ObjectMapper();

    // ===== JDBC 持久化资源（懒加载） =====
    private Connection jdbcConn;
    private PreparedStatement jdbcPs;
    private String jdbcSqlInUse;
    private int jdbcPlaceholders;

    public Sinks(Config cfg) {
        this.cfg = cfg;
    }

    // ===== 对外：单条写入 =====
    public void write(NormalizedEvent ev) throws Exception {
        if (ev == null) return;
        // JDBC
        if (cfg.sink != null && cfg.sink.jdbc != null && cfg.sink.jdbc.enabled) {
            ensureJdbcReady();
            bindJdbcParams(jdbcPs, ev, jdbcPlaceholders);
            try {
                jdbcPs.executeUpdate();
            } catch (SQLException e) {
                if (isDuplicate(e)) {
                    if (cfg.debug != null && cfg.debug.enabled) {
                        System.out.println("[sink-jdbc] duplicate skipped: " + brief(ev));
                    }
                } else {
                    dumpSqlError(e);
                    throw e;
                }
            }
        }
        // HTTP
        if (cfg.sink != null && cfg.sink.http != null && cfg.sink.http.enabled) {
            httpPostBatch(Collections.singletonList(ev));
        }
    }

    // ===== 对外：批量写入 =====
    public void write(List<NormalizedEvent> events) throws Exception {
        if (events == null || events.isEmpty()) return;

        // JDBC 批量：逐条执行（与你以前的行为一致，最小改动；后续可换成 addBatch/executeBatch）
        if (cfg.sink != null && cfg.sink.jdbc != null && cfg.sink.jdbc.enabled) {
            ensureJdbcReady();
            for (NormalizedEvent ev : events) {
                bindJdbcParams(jdbcPs, ev, jdbcPlaceholders);
                try {
                    jdbcPs.executeUpdate();
                } catch (SQLException e) {
                    if (isDuplicate(e)) {
                        if (cfg.debug != null && cfg.debug.enabled) {
                            System.out.println("[sink-jdbc] duplicate skipped: " + brief(ev));
                        }
                    } else {
                        dumpSqlError(e);
                        throw e;
                    }
                }
            }
        }

        // HTTP 批量：按 batchSize 分片发送
        if (cfg.sink != null && cfg.sink.http != null && cfg.sink.http.enabled) {
            int bs = Math.max(1, Optional.ofNullable(cfg.sink.http.batchSize).orElse(200));
            for (int i = 0; i < events.size(); i += bs) {
                int j = Math.min(i + bs, events.size());
                httpPostBatch(events.subList(i, j));
            }
        }
    }

    // ===== JDBC 实现 =====

    private void ensureJdbcReady() throws Exception {
        if (jdbcConn != null && !jdbcConn.isClosed()) {
            if (jdbcPs == null || jdbcPs.isClosed()) {
                prepareJdbcStatement();
            }
            return;
        }
        Config.Sink.Jdbc j = cfg.sink.jdbc;
        if (j.driver != null && !j.driver.isBlank()) {
            Class.forName(j.driver);
        }
        jdbcConn = DriverManager.getConnection(j.url, j.username, j.password);
        jdbcConn.setAutoCommit(true); // 维持和你原先相同的逐条提交语义
        prepareJdbcStatement();
    }

    private void prepareJdbcStatement() throws Exception {
        String sql = (cfg.sink.jdbc.insertSql != null && !cfg.sink.jdbc.insertSql.isBlank())
                ? cfg.sink.jdbc.insertSql.trim()
                : buildDefaultInsertSql();
        jdbcSqlInUse = sql;
        jdbcPlaceholders = countPlaceholders(sql);
        jdbcPs = jdbcConn.prepareStatement(sql);
        if (cfg.debug != null && cfg.debug.enabled) {
            System.out.println("[sink-jdbc] using SQL: " + jdbcSqlInUse + "  placeholders=" + jdbcPlaceholders);
        }
    }

    // 默认 13 列（与你之前示例相同）
    private String buildDefaultInsertSql() {
        return "INSERT INTO access_events (" +
                "event_time, device, direction, success," +
                "major, minor, name, employee_no, card_no, card_type," +
                "door_no, reader_no, raw_json" +
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
    }

    private int countPlaceholders(String sql) {
        int c = 0;
        for (int i = 0; i < sql.length(); i++) if (sql.charAt(i) == '?') c++;
        return c;
    }

    /**
     * 绑定参数：支持 13 或 14 个占位符（你若配置了 14 个 ? 也能兼容）
     * 13 个 ? 的顺序：
     *   1 event_time, 2 device, 3 direction, 4 success,
     *   5 major, 6 minor, 7 name, 8 employee_no, 9 card_no, 10 card_type,
     *   11 door_no, 12 reader_no, 13 raw_json
     * 14 个 ? 时，第 14 个用 employeeNo 的数值/字符串兜底（也可按你库里真实需求改成别的字段）
     */
    private void bindJdbcParams(PreparedStatement ps, NormalizedEvent ev, int ph) throws SQLException {
        int i = 1;
        set(ps, i++, ev.eventTime);                           // 1
        set(ps, i++, nv(ev.device));                          // 2
        set(ps, i++, nv(ev.direction));                       // 3
        set(ps, i++, ev.success);                             // 4
        set(ps, i++, ev.major);                               // 5
        set(ps, i++, ev.minor);                               // 6
        set(ps, i++, nv(ev.name));                            // 7
        set(ps, i++, nv(ev.employeeNo));                      // 8
        set(ps, i++, nv(ev.cardNo));                          // 9
        set(ps, i++, nv(ev.cardType));                        // 10
        set(ps, i++, ev.doorNo);                              // 11
        set(ps, i++, ev.readerNo);                            // 12
        set(ps, i++, nv(ev.rawJson));                         // 13
        if (ph >= 14) {
            // 第 14 个占位符的兜底（避免你配置里有 14 个 ? 时编译通过但运行报错）
            set(ps, i++, nv(ev.employeeNo));                  // 14
        }
    }

    private static void set(PreparedStatement ps, int idx, Object v) throws SQLException {
        if (v == null) {
            ps.setObject(idx, null);
        } else if (v instanceof Integer) {
            ps.setInt(idx, (Integer) v);
        } else if (v instanceof Long) {
            ps.setLong(idx, (Long) v);
        } else if (v instanceof Boolean) {
            ps.setBoolean(idx, (Boolean) v);
        } else {
            ps.setString(idx, String.valueOf(v));
        }
    }

    private static String nv(String s) { return (s == null ? null : s); }

    private static boolean isDuplicate(SQLException e) {
        // 常见重复：MySQL SQLState=23000，SQLite=SQLITE_CONSTRAINT 等
        String state = e.getSQLState();
        int code = e.getErrorCode();
        if ("23000".equals(state)) return true;
        String msg = e.getMessage();
        return msg != null && (msg.contains("duplicate") || msg.contains("Duplicate") || msg.contains("UNIQUE"));
    }

    private static void dumpSqlError(SQLException e) {
        System.err.println("[sink-jdbc] SQL error:");
        System.err.println("  SQLState=" + e.getSQLState() + "  errorCode=" + e.getErrorCode());
        System.err.println("  message=" + e.getMessage());
    }

    // ===== HTTP 实现 =====
    private void httpPostBatch(List<NormalizedEvent> batch) throws Exception {
        if (batch == null || batch.isEmpty()) return;
        Config.Sink.Http h = cfg.sink.http;

        String base = h.endpointBase;
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String path = (h.tableName != null && !h.tableName.isBlank())
                ? base + "/" + h.tableName
                : base + "/ingest"; // 没配表名，就走 /ingest 兜底

        // 统一的 JSON 结构：
        // { "table": "...", "rows":[{...},{...}], "count": N }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (h.tableName != null && !h.tableName.isBlank()) payload.put("table", h.tableName);
        payload.put("count", batch.size());
        payload.put("rows", batch.stream().map(this::toMap).collect(Collectors.toList()));
        String body = JSON.writeValueAsString(payload);

        BasicCredentialsProvider creds = null;
        if (h.basicUsername != null && h.basicPassword != null) {
            creds = new BasicCredentialsProvider();
            creds.setCredentials(new UsernamePasswordCredentials(h.basicUsername, h.basicPassword.toCharArray()));
        }

        try (CloseableHttpClient http = (creds == null)
                ? HttpClients.custom().build()
                : HttpClients.custom().setDefaultCredentialsProvider(creds).build()) {

            HttpPost post = new HttpPost(path);
            post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
            post.addHeader(HttpHeaders.ACCEPT, "application/json");
            post.addHeader(HttpHeaders.ACCEPT_CHARSET, StandardCharsets.UTF_8.name());
            if (h.headers != null) {
                for (Map.Entry<String, String> e : h.headers.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        post.addHeader(e.getKey(), e.getValue());
                    }
                }
            }
            if (cfg.debug != null && cfg.debug.enabled) {
                System.out.printf("[sink-http] POST %s size=%d%n", path, batch.size());
            }

            var resp = http.execute(post);
            int sc = resp.getCode();
            if (sc >= 200 && sc < 300) return;

            byte[] buf = resp.getEntity() != null ? resp.getEntity().getContent().readNBytes(2048) : new byte[0];
            System.err.printf("[sink-http] HTTP %d for %s%n", sc, path);
            if (buf.length > 0) {
                System.err.println("[sink-http] RESP (first 2KB): " + new String(buf, StandardCharsets.UTF_8));
            }
            throw new RuntimeException("sink-http: HTTP " + sc);
        }
    }

    private Map<String, Object> toMap(NormalizedEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("event_time", e.eventTime);
        m.put("device", e.device);
        m.put("direction", e.direction);
        m.put("success", e.success);
        m.put("major", e.major);
        m.put("minor", e.minor);
        m.put("name", e.name);
        m.put("employee_no", e.employeeNo);
        m.put("card_no", e.cardNo);
        m.put("card_type", e.cardType);
        m.put("door_no", e.doorNo);
        m.put("reader_no", e.readerNo);
        m.put("raw_json", e.rawJson);
        return m;
    }

    private static String brief(NormalizedEvent e) {
        return String.format("[%s] %s %s %s %s", e.eventTime, e.device, e.direction,
                e.cardNo != null ? e.cardNo : "-", e.name != null ? e.name : "-");
    }

    // ===== 关闭资源（如有需要可被调用） =====
    public void close() {
        try { if (jdbcPs != null) jdbcPs.close(); } catch (Exception ignore) {}
        try { if (jdbcConn != null) jdbcConn.close(); } catch (Exception ignore) {}
    }
}

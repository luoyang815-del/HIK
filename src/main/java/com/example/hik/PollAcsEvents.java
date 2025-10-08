package com.example.hik;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * 每 N 分钟：以“海康设备时间”为基准，抓取上一窗口的历史记录，先落库再上传。
 * - 窗口分钟数：  -Dhik.poll.windowMinutes=5   （默认 5）
 * - 轮询间隔秒：  -Dhik.poll.tickSeconds=60    （默认 60）
 * - 上传参数（任选 JVM 参数或同名环境变量，优先 JVM 参数）：
 *     -Dupload.base=http://x.x.x.x:9004
 *     -Dupload.table=access_events
 *     -Dupload.auth="Bearer xxxx"
 */
public class PollAcsEvents {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    // ---- 可配置参数读取（JVM 参数 > 环境变量）----
    private static int propInt(String key, int def) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) v = System.getenv(key.replace('.', '_').toUpperCase());
        if (v == null || v.isBlank()) return def;
        try { return Integer.parseInt(v.trim()); } catch (Exception e) { return def; }
    }
    private static String propStr(String key) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) v = System.getenv(key.replace('.', '_').toUpperCase());
        return (v == null || v.isBlank()) ? null : v;
    }
    private static int WINDOW_MINUTES() { return Math.max(1, propInt("hik.poll.windowMinutes", 5)); }
    private static int TICK_SECONDS()   { return Math.max(5,  propInt("hik.poll.tickSeconds", 60)); }

    private static String iso(OffsetDateTime t) { return t.format(ISO); }
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static String canonHistoryApi(String api) {
        if (api == null) return null;
        String a = api.trim();
        if (a.isEmpty()) return null;
        if (!a.startsWith("/")) a = "/" + a;
        if (!a.contains("format=")) a += (a.contains("?") ? "&" : "?") + "format=json";
        return a;
    }

    private static ArrayNode firstArray(JsonNode root){
        String[][] candidates = {
                {"data","list"}, {"list"},{"AcsEvent","Events"},{"Events"},{"infos"},{"rows"}
        };
        for (String[] path : candidates){
            JsonNode node = root;
            for (String seg : path){ node = node.path(seg); }
            if (node.isArray()) return (ArrayNode) node;
        }
        Iterator<JsonNode> it = root.elements();
        while (it.hasNext()){
            JsonNode n = it.next();
            if (n.isArray()) return (ArrayNode) n;
            ArrayNode sub = firstArray(n);
            if (sub != null) return sub;
        }
        return null;
    }

    /** 读取设备当前时间（优先 JSON），失败则返回 null */
    private static OffsetDateTime readDeviceNow(CloseableHttpClient client, Config.Device dev) {
        try {
            String url = HikHttp.baseUrl(dev) + "/ISAPI/System/time?format=json";
            var resp = client.execute(new HttpGet(url), r -> r);
            if (resp.getCode() == 200 && resp.getEntity() != null) {
                var node = OM.readTree(resp.getEntity().getContent());
                String ts = node.path("Time").path("localTime").asText(null);
                if (ts == null || ts.isBlank()) ts = node.path("localTime").asText(null);
                if (ts != null && !ts.isBlank()) return OffsetDateTime.parse(ts);
            }
        } catch (Exception ignore) {}
        return null;
    }

    /** 单设备抓取一个窗口：GET 分页，失败则 POST AcsEventCond 回退；返回“过滤后”的标准事件列表 */
    private static List<NormalizedEvent> fetchWindow(Config cfg, Config.Device dev,
                                                     OffsetDateTime start, OffsetDateTime end,
                                                     EventFilter filter,
                                                     CloseableHttpClient client) throws Exception {
        List<NormalizedEvent> out = new ArrayList<>(512);
        int pageNo = 1;
        final int pageSize = Math.max(50, Math.min(500,
                (cfg.sink != null && cfg.sink.http != null && cfg.sink.http.batchSize > 0)
                        ? cfg.sink.http.batchSize : 200));

        String api = (cfg != null && cfg.fetch != null) ? canonHistoryApi(cfg.fetch.historyApi) : null;

        while (true) {
            String url = (api != null)
                    ? (HikHttp.baseUrl(dev) + api
                    + "&startTime=" + enc(iso(start))
                    + "&endTime="   + enc(iso(end))
                    + "&pageNo="    + pageNo
                    + "&pageSize="  + pageSize)
                    : (HikHttp.baseUrl(dev) + "/ISAPI/AccessControl/AcsEvent?format=json"
                    + "&startTime=" + enc(iso(start))
                    + "&endTime="   + enc(iso(end))
                    + "&pageNo="    + pageNo
                    + "&pageSize="  + pageSize);

            var resp = client.execute(new HttpGet(url), r -> r);
            ArrayNode rows = null;
            if (resp.getCode()==200 && resp.getEntity()!=null) {
                var node = OM.readTree(resp.getEntity().getContent());
                rows = firstArray(node);
            }

            if (rows == null) {
                // POST 回退
                var body = OM.createObjectNode();
                var cond = body.putObject("AcsEventCond");
                cond.put("searchID", "1");
                cond.put("searchResultPosition", (pageNo-1)*pageSize);
                cond.put("maxResults", pageSize);
                cond.put("startTime", iso(start));
                cond.put("endTime",   iso(end));

                var post = new HttpPost(HikHttp.baseUrl(dev) + "/ISAPI/AccessControl/AcsEvent?format=json");
                post.addHeader("Content-Type", "application/json");
                post.setEntity(new StringEntity(body.toString(), StandardCharsets.UTF_8));

                var resp2 = client.execute(post, r -> r);
                if (resp2.getCode()==200 && resp2.getEntity()!=null) {
                    var node = OM.readTree(resp2.getEntity().getContent());
                    rows = firstArray(node);
                }
            }

            int n = rows == null ? 0 : rows.size();
            if (n == 0) break;

            for (int i = 0; i < n; i++) {
                var ne = NormalizedEvent.fromAcsInfo(rows.get(i), cfg, dev);
                if (ne != null && filter.accept(ne)) out.add(ne);
            }

            if (n < pageSize) break;
            pageNo++;
        }

        System.out.printf("[poll] %s %s ~ %s 通过筛选=%d%n", dev.name, iso(start), iso(end), out.size());
        return out;
    }

    /** 上传到数据交换平台：POST /api/sync/data/{table}，Body=JSON数组（Basic Auth：user+pass） */
    private static void uploadBatch(List<NormalizedEvent> batch) throws Exception {
        if (batch == null || batch.isEmpty()) return;

        String base  = propStr("upload.base");
        String table = propStr("upload.table");
        String user  = propStr("upload.user");
        String pass  = propStr("upload.pass");
        if (base == null || table == null || user == null || pass == null) {
            System.out.println("[upload] 跳过：缺少 -Dupload.base/-Dupload.table/-Dupload.user/-Dupload.pass");
            return;
        }
        String url = base.replaceAll("/+$","") + "/api/sync/data/" + table;

        // 映射：按你平台字段约定
        ArrayNode arr = OM.createArrayNode();
        for (NormalizedEvent ev : batch) {
            ObjectNode o = OM.createObjectNode();
            o.put("eventTime", ev.eventTime);
            o.put("device", ev.device);
            o.put("direction", ev.direction);
            if (ev.success == null) o.putNull("success"); else o.put("success", ev.success ? 1 : 0);
            if (ev.major == null) o.putNull("major"); else o.put("major", ev.major);
            if (ev.minor == null) o.putNull("minor"); else o.put("minor", ev.minor);
            o.put("name", ev.name);
            o.put("employeeNo", ev.employeeNo);
            o.put("cardNo", ev.cardNo);
            o.put("cardType", ev.cardType);
            if (ev.doorNo == null) o.putNull("doorNo"); else o.put("doorNo", ev.doorNo);
            if (ev.readerNo == null) o.putNull("readerNo"); else o.put("readerNo", ev.readerNo);
            o.put("rawJson", ev.rawJson);
            arr.add(o);
        }

        // Basic Auth 头
        String basic = java.util.Base64.getEncoder()
                .encodeToString((user + ":" + pass).getBytes(java.nio.charset.StandardCharsets.UTF_8));

        try (CloseableHttpClient http = HttpClients.createDefault()) {
            var post = new HttpPost(url);
            post.addHeader("Authorization", "Basic " + basic);
            post.addHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(arr.toString(), StandardCharsets.UTF_8));

            var resp = http.execute(post, r -> r);
            String body = resp.getEntity()!=null
                    ? new String(resp.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8) : "";
            System.out.printf("[upload] HTTP %d, %s%n", resp.getCode(), body);
        }
    }


    /** 主循环：每次以“设备时间”为准推进 window 窗口 */
    public static void run(Config cfg) {
        var devices = (cfg.devices!=null && !cfg.devices.isEmpty())
                ? cfg.devices : List.of(cfg.device);

        // 每台设备各自维护“已处理到的设备时间”
        var lastEndMap = new HashMap<String, OffsetDateTime>();

        while (true) {
            for (Config.Device dev : devices) {
                try (CloseableHttpClient client = HikHttp.build(cfg, dev)) {
                    OffsetDateTime deviceNow = readDeviceNow(client, dev);
                    if (deviceNow == null) deviceNow = OffsetDateTime.now(ZoneOffset.ofHours(+8));

                    int winMin = WINDOW_MINUTES();
                    OffsetDateTime lastEnd = lastEndMap.get(dev.name);
                    OffsetDateTime start = (lastEnd != null) ? lastEnd : deviceNow.minusMinutes(winMin);
                    OffsetDateTime end   = start.plusMinutes(winMin);
                    if (end.isAfter(deviceNow)) end = deviceNow;
                    if (!end.isAfter(start)) {
                        System.out.printf("[poll] %s 设备时间=%s, 暂无新窗口，跳过%n", dev.name, iso(deviceNow));
                        continue;
                    }

                    System.out.printf("[poll] %s deviceNow=%s 窗口=%s ~ %s (win=%dm)%n",
                            dev.name, iso(deviceNow), iso(start), iso(end), winMin);

                    var filter = new EventFilter(cfg, dev);
                    var sinks  = new Sinks(cfg);

                    var all = fetchWindow(cfg, dev, start, end, filter, client);
                    if (!all.isEmpty()) {
                        sinks.flushBatch(all);   // 先落库
                        uploadBatch(all);        // 再上传（若配置了 upload.*）
                    }

                    // 推进游标：+1s 防重叠
                    lastEndMap.put(dev.name, end.plusSeconds(1));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            try { Thread.sleep(TICK_SECONDS() * 1000L); } catch (InterruptedException ignore) {}
        }
    }
}

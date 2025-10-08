package com.example.hik;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 实时拉取（设备时间基准 + 强诊断 + 自适应窗口）
 * - 先取设备时间 /ISAPI/System/time?format=json，基于设备时间切片
 * - 窗口：sliceMinutes(默认1)；落库延迟：lagSeconds(默认10)；初始回溯：initBackMinutes(默认5)
 *   可用JVM参数或环境变量覆盖：hik.stream.sliceMinutes / initBackMinutes / lagSeconds
 * - historyApi 自动补 "/" 和 "format=json"
 * - GET 分页失败则回退 POST AcsEventCond
 * - 打印 DeviceNow/Window/URL(首个)/Raw vs Filter，通过“自适应扩窗(到3min)”提高容错
 */
public class StreamAcsEvents {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private static int  propInt(String key, int def){
        String v = System.getProperty(key);
        if (v==null || v.isBlank()) v = System.getenv(key.replace('.','_').toUpperCase());
        if (v==null || v.isBlank()) return def;
        try { return Integer.parseInt(v.trim()); } catch (Exception e){ return def; }
    }
    private static Duration SLICE() { return Duration.ofMinutes(Math.max(1, propInt("hik.stream.sliceMinutes", 1))); }
    private static Duration INIT_BACK() { return Duration.ofMinutes(Math.max(1, propInt("hik.stream.initBackMinutes", 5))); }
    private static Duration LAG() { return Duration.ofSeconds(Math.max(1, propInt("hik.stream.lagSeconds", 10))); }

    private static String iso(OffsetDateTime t){ return t.format(ISO); }
    private static String enc(String s){ return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    /** 规范化 historyApi：确保以 / 开头，且包含 format=json */
    private static String canonHistoryApi(String api){
        if (api == null) return null;
        String a = api.trim();
        if (a.isEmpty()) return null;
        if (!a.startsWith("/")) a = "/" + a;
        if (!a.contains("format=")) a = a + (a.contains("?") ? "&" : "?") + "format=json";
        return a;
    }

    /** 优先按路径找数组，否则深度遍历回第一个数组 */
    private static ArrayNode findFirstArray(JsonNode root, String... paths){
        for (String p : paths) {
            JsonNode node = root;
            for (String seg : p.split("\\.")) {
                if (node == null) break;
                node = node.path(seg);
            }
            if (node != null && node.isArray()) return (ArrayNode) node;
        }
        Iterator<JsonNode> it = root.elements();
        while (it.hasNext()){
            JsonNode n = it.next();
            if (n.isArray()) return (ArrayNode) n;
            var found = findFirstArray(n, paths);
            if (found != null) return found;
        }
        return null;
    }

    /** 读取设备当前时间（失败则返回 null）；优先 JSON 格式 */
    private static OffsetDateTime readDeviceNow(CloseableHttpClient client, Config.Device dev) {
        try {
            var url = HikHttp.baseUrl(dev) + "/ISAPI/System/time?format=json";
            var req = new HttpGet(url);
            var resp = client.execute(req, r -> r);
            if (resp.getCode()==200 && resp.getEntity()!=null) {
                var node = OM.readTree(resp.getEntity().getContent());
                // 常见结构：{"Time":{"localTime":"2025-09-29T15:36:58+08:00", ...}}
                String ts = node.path("Time").path("localTime").asText(null);
                if (ts == null || ts.isBlank()) {
                    // 有的机型用 "localTime" 顶层
                    ts = node.path("localTime").asText(null);
                }
                if (ts != null && !ts.isBlank()) {
                    return OffsetDateTime.parse(ts);
                }
            }
        } catch (Exception ignore) { }
        return null;
    }

    /** 回退：POST /ISAPI/AccessControl/AcsEvent?format=json，使用 AcsEventCond 搜索 */
    private static ArrayNode trySearchMode(CloseableHttpClient client,
                                           Config.Device dev,
                                           OffsetDateTime start,
                                           OffsetDateTime end,
                                           int offset, int limit) throws Exception {
        String url = HikHttp.baseUrl(dev) + "/ISAPI/AccessControl/AcsEvent?format=json";

        var body = OM.createObjectNode();
        var cond = body.putObject("AcsEventCond");
        cond.put("searchID", "1");
        cond.put("searchResultPosition", offset);
        cond.put("maxResults", limit);
        cond.put("startTime", iso(start));
        cond.put("endTime",   iso(end));

        var post = new HttpPost(url);
        post.addHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(body.toString(), StandardCharsets.UTF_8));

        var resp = client.execute(post, r -> r);
        if (resp.getCode()!=200 || resp.getEntity()==null) return null;

        var node = OM.readTree(resp.getEntity().getContent());
        return findFirstArray(node, "AcsEvent.Events", "Events", "list");
    }

    /** 抓取一个时间窗（含 GET 分页与 POST 回退），返回过滤后的事件，并打印“原始/筛选后”计数 */
    private static List<NormalizedEvent> fetchWindow(Config cfg, Config.Device dev,
                                                     OffsetDateTime winStart, OffsetDateTime winEnd,
                                                     EventFilter filter,
                                                     CloseableHttpClient client,
                                                     boolean printFirstUrl) throws Exception {
        List<NormalizedEvent> out = new ArrayList<>(256);
        int rawCnt = 0;

        int pageNo = 1;
        final int pageSize = Math.max(50, Math.min(500,
                (cfg.sink!=null && cfg.sink.http!=null && cfg.sink.http.batchSize>0)
                        ? cfg.sink.http.batchSize : 200));

        String api = canonHistoryApi(cfg!=null && cfg.fetch!=null ? cfg.fetch.historyApi : null);
        String firstUrlPrinted = null;

        while (true) {
            String url;
            if (api != null) {
                url = HikHttp.baseUrl(dev) + api
                        + "&startTime=" + enc(iso(winStart))
                        + "&endTime="   + enc(iso(winEnd))
                        + "&pageNo="    + pageNo
                        + "&pageSize="  + pageSize;
            } else {
                url = HikHttp.baseUrl(dev) + "/ISAPI/AccessControl/AcsEvent?format=json"
                        + "&startTime=" + enc(iso(winStart))
                        + "&endTime="   + enc(iso(winEnd))
                        + "&pageNo="    + pageNo
                        + "&pageSize="  + pageSize;
            }

            if (printFirstUrl && firstUrlPrinted == null) {
                firstUrlPrinted = url;
                System.out.println("[stream] first URL: " + url);
            }

            var req = new HttpGet(url);
            var resp = client.execute(req, r -> r);
            int code = resp.getCode();

            ArrayNode rows = null;
            if (code == 200 && resp.getEntity()!=null) {
                var node = OM.readTree(resp.getEntity().getContent());
                rows = findFirstArray(node, "data.list", "list", "AcsEvent.Events", "Events", "infos", "rows");
            }
            if (rows == null) {
                rows = trySearchMode(client, dev, winStart, winEnd, (pageNo-1)*pageSize, pageSize);
            }

            int batchCount = rows == null ? 0 : rows.size();
            if (batchCount == 0) break;

            for (int i = 0; i < batchCount; i++) {
                rawCnt++;
                var ev = rows.get(i);
                var ne = NormalizedEvent.fromAcsInfo(ev, cfg, dev);
                if (ne == null) continue;
                if (filter.accept(ne)) out.add(ne);
            }

            if (batchCount < pageSize) break;
            pageNo++;
        }

        System.out.printf("[stream] 窗口 %s ~ %s 原始=%d 通过筛选=%d%n",
                iso(winStart), iso(winEnd), rawCnt, out.size());
        return out;
    }

    /** 实时追尾主循环（设备时间基准 + 自适应扩窗） */
    public static void run(Config cfg) throws Exception {
        var devices = (cfg.devices!=null && !cfg.devices.isEmpty())
                ? cfg.devices : List.of(cfg.device);

        while (true) {
            for (Config.Device dev : devices) {
                try (CloseableHttpClient client = HikHttp.build(cfg, dev)) {

                    // 1) 用设备时间为基准；失败则退回本机 +8（兼容你现有环境）
                    OffsetDateTime deviceNow = readDeviceNow(client, dev);
                    if (deviceNow == null) {
                        deviceNow = OffsetDateTime.now(ZoneOffset.ofHours(+8));
                    }

                    // 2) 计算本轮窗口（now - LAG，回溯 INIT_BACK，按 SLICE 切片逐段抓）
                    OffsetDateTime endCursor = deviceNow.minus(LAG());
                    OffsetDateTime startCursor = endCursor.minus(INIT_BACK());

                    Duration slice = SLICE();
                    boolean printedUrl = false;

                    while (startCursor.isBefore(endCursor)) {
                        OffsetDateTime winStart = startCursor;
                        OffsetDateTime winEnd   = winStart.plus(slice);
                        if (winEnd.isAfter(endCursor)) winEnd = endCursor;

                        // 诊断输出核心时间参考
                        System.out.printf("[stream] deviceNow=%s winStart=%s winEnd=%s slice=%s lag=%ss%n",
                                iso(deviceNow), iso(winStart), iso(winEnd),
                                String.valueOf(slice.toMinutes()), String.valueOf(LAG().toSeconds()));

                        var sinks  = new Sinks(cfg);
                        var filter = new EventFilter(cfg, dev);

                        var buf = new ArrayList<NormalizedEvent>(512);
                        buf.addAll(fetchWindow(cfg, dev, winStart, winEnd, filter, client, !printedUrl));
                        printedUrl = true;

                        // 若这一片“原始=0”，尝试扩大窗口到3分钟再试一遍
                        if (buf.isEmpty() && slice.toMinutes() < 3) {
                            OffsetDateTime winEnd2 = winStart.plusMinutes(3);
                            if (winEnd2.isAfter(endCursor)) winEnd2 = endCursor;
                            if (winEnd2.isAfter(winEnd)) {
                                System.out.printf("[stream] empty slice, retry with 3min window: %s ~ %s%n",
                                        iso(winStart), iso(winEnd2));
                                buf.addAll(fetchWindow(cfg, dev, winStart, winEnd2, filter, client, false));
                                // 注意：不改变推进节奏，仅此片尝试扩大
                                winEnd = winEnd2;
                            }
                        }

                        if (!buf.isEmpty()) sinks.flushBatch(buf);

                        // +1s 防重复
                        startCursor = winEnd.plusSeconds(1);
                    }
                }
            }

            // 周期性轮询（可调）
            Thread.sleep(1000);
        }
    }
}

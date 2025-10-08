package com.example.hik;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.StringEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

public class PullAcsEvents {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final XmlMapper XML = new XmlMapper();
  private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

  public static void pullOne(Config cfg, Config.Device dev, String startIso, String endIso) throws Exception {
    final EventFilter filterF = new EventFilter(cfg, dev);
    final Sinks sinksF = new Sinks(cfg);
    final int pageSize = 200;

    try (CloseableHttpClient client = HikHttp.build(cfg, dev)) {
      ZonedDateTime start = parseIso(startIso);
      ZonedDateTime end   = parseIso(endIso);

      for (ZonedDateTime dayStart = start; !dayStart.isAfter(end); dayStart = dayStart.plusDays(1)) {
        ZonedDateTime dayEnd = dayStart.withHour(23).withMinute(59).withSecond(59);
        if (dayEnd.isAfter(end)) dayEnd = end;

        String s = dayStart.format(ISO);
        String e = dayEnd.format(ISO);

        int position = 0;
        int page = 0;

        while (true) {
          page++;
          final int pageF = page;              // ★ 关键：把 page 捕获为 final
          String url;
          String payloadXml;

          if ("LogSearch".equalsIgnoreCase(cfg.fetch.historyApi)) {
            url = HikHttp.baseUrl(dev) + "/ISAPI/AccessControl/LogSearch";
            payloadXml = ""
              + "<AcsEventSearchDescription>"
              + "<searchID>1</searchID>"
              + "<searchResultPosition>"+position+"</searchResultPosition>"
              + "<maxResults>"+pageSize+"</maxResults>"
              + "<timeRange><startTime>"+s+"</startTime><endTime>"+e+"</endTime></timeRange>"
              + "<AcsEventType><major>0</major><minor>0</minor></AcsEventType>"
              + "</AcsEventSearchDescription>";
          } else {
            url = HikHttp.baseUrl(dev) + "/ISAPI/AccessControl/AcsEvent";
            payloadXml = ""
              + "<AcsEventCond>"
              + "<searchID>1</searchID>"
              + "<searchResultPosition>"+position+"</searchResultPosition>"
              + "<maxResults>"+pageSize+"</maxResults>"
              + "<major>0</major><minor>0</minor>"
              + "<startTime>"+s+"</startTime><endTime>"+e+"</endTime>"
              + "</AcsEventCond>";
            if (cfg.fetch.preferJson) url += "?format=json";
          }

          final String urlF = url;
          final boolean preferJsonF = cfg.fetch.preferJson;
          final boolean isLogSearchF = "LogSearch".equalsIgnoreCase(cfg.fetch.historyApi);
          final String payloadXmlF = payloadXml;
          final String devNameF = (dev.name != null ? dev.name : dev.host);
          final int positionF = position;

          HttpPost post = new HttpPost(urlF);
          post.addHeader(HttpHeaders.ACCEPT, preferJsonF ? "application/json" : "application/xml");
          post.addHeader(HttpHeaders.CONTENT_TYPE, preferJsonF ? "application/json; charset=UTF-8" : "application/xml; charset=UTF-8");

          if (preferJsonF && !isLogSearchF) {
            String json = "{\"AcsEventCond\":{\"searchID\":\"1\",\"searchResultPosition\":"+positionF+",\"maxResults\":"+pageSize+",\"major\":0,\"minor\":0,\"startTime\":\""+s+"\",\"endTime\":\""+e+"\"}}";
            post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8)));
          } else {
            post.setEntity(new StringEntity(payloadXmlF, ContentType.APPLICATION_XML.withCharset(StandardCharsets.UTF_8)));
          }

          int gotThisPage = client.execute(post, resp -> {
            int code = resp.getCode();
            String body = "";
            try { body = new String(resp.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8); } catch (Exception ignore) {}
            System.out.println("[DEBUG]["+devNameF+"] HTTP "+code+" "+urlF+" pos="+positionF+" page="+pageF);
            if (code / 100 != 2) {
              System.out.println("[DEBUG] ERROR BODY (first 2KB):\n" + body.substring(0, Math.min(body.length(), 2048)));
              throw new RuntimeException("HTTP " + code);
            }

            try {
              int got = 0;
              if (preferJsonF && !isLogSearchF) {
                JsonNode root = JSON.readTree(body);
                JsonNode info = root.path("AcsEvent").path("InfoList");
                if (info.isArray()) {
                  List<NormalizedEvent> buf = new ArrayList<>();
                  for (JsonNode ev : info) {
                    NormalizedEvent ne = NormalizedEvent.fromAcsInfo(ev, cfg, dev);
                    ne.device = (dev.name != null && !dev.name.isEmpty() ? dev.name : (dev.host + ":" + dev.port));
                    if (filterF.accept(ne)) {
                      if (ne.eventTime == null || (ne.name == null && ne.employeeNo == null && ne.cardNo == null)) continue;
                      buf.add(ne); got++;
                      if (buf.size() >= 200) { sinksF.flushBatch(buf); buf.clear(); }
                    }
                  }
                  if (!buf.isEmpty()) sinksF.flushBatch(buf);
                }
                return got;
              } else {
                JsonNode root = XML.readTree(body.getBytes(StandardCharsets.UTF_8));
                JsonNode info = root.path("AcsEvent").path("InfoList");
                if (!info.isArray()) {
                  info = root.path("AcsEventSearchResult").path("MatchList");
                  if (!info.isArray()) info = root.path("AcsEventSearchResult").path("Items");
                  if (info.has("Item")) info = info.get("Item");
                }
                if (info.isArray()) {
                  List<NormalizedEvent> buf = new ArrayList<>();
                  for (JsonNode ev : info) {
                    NormalizedEvent ne = NormalizedEvent.fromAcsInfo(ev, cfg, dev);
                    ne.device = (dev.name != null && !dev.name.isEmpty() ? dev.name : (dev.host + ":" + dev.port));
                    if (filterF.accept(ne)) {
                      if (ne.eventTime == null || (ne.name == null && ne.employeeNo == null && ne.cardNo == null)) continue;
                      buf.add(ne); got++;
                      if (buf.size() >= 200) { sinksF.flushBatch(buf); buf.clear(); }
                    }
                  }
                  if (!buf.isEmpty()) sinksF.flushBatch(buf);
                }
                return got;
              }
            } catch (Exception pe) {
              throw new RuntimeException("parse error: " + pe.getMessage());
            }
          });

          if (gotThisPage <= 0) break;    // 本页无数据，结束该日
          position += gotThisPage;
          //if (gotThisPage < pageSize) break; // 最后一页
        }
      }
    }
  }

  private static ZonedDateTime parseIso(String s) {
    try { return OffsetDateTime.parse(s).toZonedDateTime(); } catch (Exception ignore) {}
    try { return LocalDateTime.parse(s).atZone(ZoneId.systemDefault()); } catch (Exception ignore) {}
    return ZonedDateTime.now();
  }
}

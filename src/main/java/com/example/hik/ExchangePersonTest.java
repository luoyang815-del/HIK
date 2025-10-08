package com.example.hik;

import com.example.hik.exchange.ExchangeClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ExchangePersonTest {
    public static void main(String[] args) throws Exception {
        // ======== CONFIG (edit here or wire to your Config/application.yaml) ========
        String baseUrl  = "http://192.168.1.11:9004";
        String path     = "/api/sync/data/cross_record_person";
        String aesKey   = "HAYJJWWYTSJJHMYH";

        // BASIC auth
        String authType = "basic";             // "basic" or "login"
        String username = "your_user";
        String password = "your_pass";

        // LOGIN FLOW (only used if authType="login")
        String loginPath = "/api/auth/login";
        String loginCT = "application/json";
        String loginBodyTemplate = "{\"username\":\"${username}\",\"password\":\"${password}\"}";
        String tokenField = "token";
        String tokenHeaderName = "Authorization";
        String tokenHeaderFormat = "Bearer %s";

        ExchangeClient client = new ExchangeClient(
                baseUrl, path, aesKey,
                authType, username, password,
                loginPath, loginCT, loginBodyTemplate, tokenField, tokenHeaderName, tokenHeaderFormat
        );

        // ======== SAMPLE ONE RECORD ========
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("entranceName",  "西卡口");
        row.put("crossOut",      "进场");     // 进场/出场
        row.put("crossTime",     now);        // yyyy-MM-dd HH:mm:ss
        row.put("secondGate",    "0");        // 0：是二道门；1：否
        row.put("type",          "访客");     // 访客/企业员工/其他
        row.put("name",          "张三");
        row.put("phone",         "13800000000");
        row.put("enterpriseId",  "1234567890");
        row.put("enterpriseName","某某科技有限公司");
        row.put("releaseWay",    "自动放行");

        String resp = client.uploadPersonCrossRecords(List.of(row));
        System.out.println("上传响应：" + resp);
    }
}

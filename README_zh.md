# hik-remote-access-events-sink v1.0.8c (v10 包)
- 历史拉取增强：**按天切片 + 分页**（searchResultPosition）
- 只入库**认证通过**记录（默认 `onlySuccess: true`），丢弃明显空白记录
- **HTTPS + insecureTLS**：信任所有证书并关闭主机名校验
- 你的设备适配：**AcsEvent + JSON**

## 构建
```
mvn -q -DskipTests package
```
生成：`target/hik-remote-access-events-sink-1.0.8c.jar`

## 运行示例
```
java -Dconfig=./config/application.yaml -Djdk.tls.client.protocols=TLSv1.2   -jar target/hik-remote-access-events-sink-1.0.8c.jar   pull "2025-09-11T00:00:00+08:00" "2025-09-18T23:59:59+08:00"
```

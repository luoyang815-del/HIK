@echo off
setlocal
where mvn >nul 2>nul
if errorlevel 1 (
  echo [ERROR] 未检测到 Maven（mvn）。请先安装 Maven。
  exit /b 1
)
echo [1/3] 打包...
mvn -q -DskipTests package || (echo [ERROR] mvn package 失败 & exit /b 2)
echo [2/3] 运行示例 pull...
java -Dconfig=.\config\application.yaml -Djdk.tls.client.protocols=TLSv1.2 -jar target\hik-remote-access-events-sink-1.0.8c.jar pull "2025-09-11T00:00:00+08:00" "2025-09-18T23:59:59+08:00"

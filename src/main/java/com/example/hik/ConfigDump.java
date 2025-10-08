package com.example.hik;

public class ConfigDump {
    public static void main(String[] args) throws Exception {
        Config cfg = Config.load();
        if (cfg.devices == null || cfg.devices.isEmpty()) {
            System.out.println("NO DEVICES in config.");
            return;
        }
        System.out.println("Devices count = " + cfg.devices.size());
        for (Config.Device d : cfg.devices) {
            boolean https = true;
            int port = 0;
            try { https = d.https; } catch (Throwable ignore) {}
            try { port = d.port; } catch (Throwable ignore) {}
            if (port <= 0) port = https ? 443 : 80;

            System.out.printf("name=%s host=%s port=%d https=%s insecureTLS=%s user=%s%n",
                d.name, d.host, port, Boolean.toString(https), d.insecureTLS, d.username);
            String base = (https ? "https" : "http") + "://" + (d.host == null ? "" : d.host.trim()) + ":" + port;
            System.out.println("safeBaseUrl=" + base);
        }
    }
}

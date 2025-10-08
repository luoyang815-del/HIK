package com.example.hik.client;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.ssl.TrustStrategy;

import javax.net.ssl.SSLContext;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Duration;

/**
 * Builds an {@link CloseableHttpClient} that optionally trusts every certificate.
 */
public final class InsecureTlsHttpClientFactory {
    private InsecureTlsHttpClientFactory() {
    }

    public static CloseableHttpClient create(boolean insecure, Duration timeout) {
        long millis = timeout == null ? 0L : timeout.toMillis();
        if (millis <= 0L) {
            millis = 30_000L;
        }
        Timeout requestTimeout = Timeout.ofMilliseconds(millis);
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(requestTimeout)
            .setConnectTimeout(requestTimeout)
            .setResponseTimeout(requestTimeout)
            .build();
        if (!insecure) {
            return HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
        }
        try {
            SSLContext context = SSLContexts.custom()
                .loadTrustMaterial(null, (TrustStrategy) (X509Certificate[] chain, String authType) -> true)
                .build();
            return HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setSSLContext(context)
                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                .setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(requestTimeout).build())
                .build();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to create insecure TLS client", ex);
        }
    }
}

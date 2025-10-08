
package com.example.hik;

import org.apache.hc.client5.http.auth.*; import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider; import org.apache.hc.client5.http.impl.classic.*; import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager; import org.apache.hc.core5.util.Timeout; import org.apache.hc.core5.ssl.*;
import javax.net.ssl.SSLContext; import java.security.*; import java.security.cert.X509Certificate;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;

public class HikHttp {
  public static CloseableHttpClient build(Config cfg, Config.Device dev) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
    BasicCredentialsProvider cp=new BasicCredentialsProvider();
    cp.setCredentials(new AuthScope(null,-1), new UsernamePasswordCredentials(dev.username, dev.password.toCharArray()));
    RequestConfig rc=RequestConfig.custom().setConnectTimeout(Timeout.ofSeconds(cfg.fetch.timeoutSeconds)).setResponseTimeout(Timeout.ofSeconds(cfg.fetch.timeoutSeconds)).build();
    if(dev.https && dev.insecureTLS){
      TrustStrategy trustAll=(X509Certificate[] chain,String authType)->true;
      SSLContext ssl=SSLContexts.custom().loadTrustMaterial(null,trustAll).build();
      HttpClientConnectionManager cm=PoolingHttpClientConnectionManagerBuilder.create().setSSLSocketFactory(new org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory(ssl, NoopHostnameVerifier.INSTANCE)).build();
      return HttpClients.custom().setDefaultCredentialsProvider(cp).setConnectionManager(cm).setDefaultRequestConfig(rc).build();
    }
    return HttpClients.custom().setDefaultCredentialsProvider(cp).setDefaultRequestConfig(rc).build();
  }
  public static String baseUrl(Config.Device dev){ return (dev.https?"https":"http")+"://"+dev.host+":"+dev.port; }
}

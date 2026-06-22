package com.psp.npci.adapter.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.security.KeyStore;

/**
 * RestTemplate configuration for outbound NPCI calls.
 *
 * <h2>mTLS design</h2>
 * <p>
 * The flag {@code npci.mtls.enabled} controls which RestTemplate is wired:
 * <ul>
 * <li>{@code false} (default) — plain HTTP RestTemplate with configurable timeouts.</li>
 * <li>{@code true} — same Apache HttpClient 5 client but with an {@link SSLContext}
 * loaded from the keystore/truststore paths supplied via environment variables.</li>
 * </ul>
 *
 * <h2>Timeout configuration</h2>
 * <ul>
 * <li>{@code npci.http.connect-timeout-ms} — TCP connection timeout (default 5000ms)</li>
 * <li>{@code npci.http.read-timeout-ms} — response read timeout (default 30000ms)</li>
 * </ul>
 */
@Slf4j
@Configuration
public class RestTemplateConfig {

    @Value("${npci.mtls.enabled:false}")
    private boolean mtlsEnabled;

    @Value("${npci.mtls.keystore-path:}")
    private String keystorePath;

    @Value("${npci.mtls.keystore-password:}")
    private String keystorePassword;

    @Value("${npci.mtls.truststore-path:}")
    private String truststorePath;

    @Value("${npci.mtls.truststore-password:}")
    private String truststorePassword;

    @Value("${npci.http.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${npci.http.read-timeout-ms:30000}")
    private int readTimeoutMs;

    @Value("${npci.http.max-connections:50}")
    private int maxConnections;

    /**
     * Builds a {@link RestTemplate} backed by Apache HttpClient 5 with explicit
     * connect and read timeouts sourced from application configuration.
     */
    @Bean
    public RestTemplate npciRestTemplate() {
        try {
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                    .setResponseTimeout(Timeout.ofMilliseconds(readTimeoutMs))
                    .build();

            CloseableHttpClient httpClient;

            if (mtlsEnabled) {
                log.info("[NPCI-ADAPTER] mTLS ENABLED — loading keystore from {}", keystorePath);

                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                try (FileInputStream ksStream = new FileInputStream(keystorePath)) {
                    keyStore.load(ksStream, keystorePassword.toCharArray());
                }

                KeyStore trustStore = KeyStore.getInstance("PKCS12");
                try (FileInputStream tsStream = new FileInputStream(truststorePath)) {
                    trustStore.load(tsStream, truststorePassword.toCharArray());
                }

                SSLContext sslContext = SSLContextBuilder.create()
                        .loadKeyMaterial(keyStore, keystorePassword.toCharArray())
                        .loadTrustMaterial(trustStore, null)
                        .build();

                var sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                        .setSslContext(sslContext)
                        .build();

                var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(sslSocketFactory)
                        .setMaxConnTotal(maxConnections)
                        .build();

                httpClient = HttpClients.custom()
                        .setConnectionManager(connectionManager)
                        .setDefaultRequestConfig(requestConfig)
                        .build();

                log.info("[NPCI-ADAPTER] mTLS RestTemplate built | connectTimeout={}ms readTimeout={}ms",
                        connectTimeoutMs, readTimeoutMs);

            } else {
                log.info("[NPCI-ADAPTER] mTLS DISABLED — plain HTTP RestTemplate | connectTimeout={}ms readTimeout={}ms",
                        connectTimeoutMs, readTimeoutMs);

                var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(maxConnections)
                        .build();

                httpClient = HttpClients.custom()
                        .setConnectionManager(connectionManager)
                        .setDefaultRequestConfig(requestConfig)
                        .build();
            }

            HttpComponentsClientHttpRequestFactory factory =
                    new HttpComponentsClientHttpRequestFactory(httpClient);

            return new RestTemplate(factory);

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "[NPCI-ADAPTER] Failed to build RestTemplate (mTLS=" + mtlsEnabled + ")", ex);
        }
    }
}

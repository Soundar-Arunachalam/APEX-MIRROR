package com.pspswitch.tpapegress.client;

import com.pspswitch.tpapegress.exception.WebhookDeliveryException;
import com.pspswitch.tpapegress.model.payload.WebhookPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

/**
 * Thin WebClient wrapper for outbound webhook HTTP POSTs.
 *
 * ADR-005: No retry logic inside this class.
 *          Makes exactly ONE HTTP call with no internal retry and returns the status code.
 *          All retry orchestration lives in the handler.
 *
 * @since 1.0
 */
@Component
@Slf4j
public class WebhookHttpClient {

    private final WebClient webClient;
    private final int timeoutSeconds;

    /**
     * Instantiates the client with specific web builder and timeout setups.
     *
     * @param webClientBuilder Spring-injected builder
     * @param timeoutSeconds configurable timeout length
     */
    public WebhookHttpClient(
            WebClient.Builder webClientBuilder,
            @Value("${tpap.egress.webhook.timeout-seconds:5}") int timeoutSeconds) {
        this.webClient = webClientBuilder.build();
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * POST the webhook payload to the given URL making exactly ONE HTTP call with no internal retry (ADR-005).
     *
     * @param url the destination URL where the payload is posted
     * @param payload the generic {@link WebhookPayload} format wrapping domain events
     * @return HTTP status code (2xx, 4xx, 5xx) representing response from target
     * @throws WebhookDeliveryException on network failure (timeout, connection refused)
     */
    public int post(String url, WebhookPayload payload) {
        try {
            var response = webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(timeoutSeconds));

            return response != null ? response.getStatusCode().value() : 0;
        } catch (WebClientResponseException e) {
            // Server returned a non-2xx status — extract the code
            return e.getStatusCode().value();
        } catch (WebClientRequestException e) {
            throw new WebhookDeliveryException(e.getMessage(), e);
        } catch (Exception e) {
            throw new WebhookDeliveryException(e.getMessage(), e);
        }
    }
}

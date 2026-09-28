package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.config.AppProperties;
import com.ecomerceshopeonline.ecomercewepapp.exception.ApiException;
import com.ecomerceshopeonline.ecomercewepapp.service.BakongClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class BakongApiClient implements BakongClient {

    private static final String CHECK_TRANSACTION = "/v1/check_transaction_by_md5";
    private static final String CHECK_ACCOUNT = "/v1/check_bakong_account";
    private static final int RESPONSE_CODE_SUCCESS = 0;

    private final AppProperties.Bakong config;
    private final RestClient restClient;

    public BakongApiClient(AppProperties properties) {
        this.config = properties.bakong();
        // RestClient.builder() rather than the injected RestClient.Builder: this client talks
        // to one fixed external host and should not inherit app-wide interceptors.
        this.restClient = RestClient.builder()
                .baseUrl(config.apiUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /** Bakong wraps every reply in this envelope; responseCode 0 means success. */
    private record Envelope(Integer responseCode, String responseMessage, Map<String, Object> data) {
    }

    @Override
    public TransactionCheck checkTransaction(String md5) {
        Envelope envelope = post(CHECK_TRANSACTION, Map.of("md5", md5));

        if (envelope == null) {
            return TransactionCheck.pending("No response from Bakong");
        }

        Map<String, Object> data = envelope.data() == null ? Map.of() : envelope.data();
        String hash = asString(data.get("hash"));
        String status = asString(data.get("status"));

        // Bakong signals a settled transaction either by responseCode 0 or by returning a
        // transaction hash. Treat both as paid, matching the reference PHP implementation.
        boolean paid = envelope.responseCode() != null
                && envelope.responseCode() == RESPONSE_CODE_SUCCESS
                || hash != null && !hash.isBlank()
                || "SUCCESS".equalsIgnoreCase(status);

        if (!paid) {
            return TransactionCheck.pending(
                    envelope.responseMessage() == null ? "Awaiting payment" : envelope.responseMessage());
        }
        return new TransactionCheck(true, hash, asString(data.get("fromAccountId")),
                "Payment confirmed by Bakong");
    }

    @Override
    public Optional<String> resolveAccountName(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return Optional.empty();
        }
        Envelope envelope = post(CHECK_ACCOUNT, Map.of("accountId", accountId));
        if (envelope == null || envelope.responseCode() == null
                || envelope.responseCode() != RESPONSE_CODE_SUCCESS || envelope.data() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(asString(envelope.data().get("accountName")));
    }

    // ------------------------------------------------------------------ plumbing

    private Envelope post(String path, Map<String, Object> body) {
        requireToken();
        try {
            return restClient.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.token())
                    .body(body)
                    .retrieve()
                    // Bakong answers 4xx for "not found yet", which is not an error for polling.
                    .onStatus(status -> status.is4xxClientError(), (req, res) -> {
                        log.debug("Bakong {} returned {}", path, res.getStatusCode());
                    })
                    .body(Envelope.class);
        } catch (RestClientException ex) {
            log.error("Bakong {} is unreachable", path, ex);
            throw new ApiException("Could not reach Bakong. Please try again in a moment.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private void requireToken() {
        if (!config.canQueryApi()) {
            throw new ApiException(
                    "Bakong API token is not configured. Set app.bakong.token.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

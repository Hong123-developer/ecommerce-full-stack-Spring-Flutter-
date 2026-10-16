package com.ecomerceshopeonline.ecomercewepapp.controller;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.ApiResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PaymentMethodResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PaymentResponse;
import com.ecomerceshopeonline.ecomercewepapp.exception.BadRequestException;
import com.ecomerceshopeonline.ecomercewepapp.security.CustomUserDetails;
import com.ecomerceshopeonline.ecomercewepapp.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    /** Plenty for PayWay's pushback; anything bigger is not one. */
    private static final int MAX_CALLBACK_BODY = 64 * 1024;
    /** What an attempt id can look like; anything else is not ours (and is not logged). */
    private static final Pattern SAFE_TRAN_ID = Pattern.compile("[A-Za-z0-9_-]{1,40}");

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    /**
     * Public: which payment methods checkout may offer right now. Card appears as enabled
     * only once the card provider's credentials are configured.
     */
    @GetMapping("/methods")
    public ApiResponse<List<PaymentMethodResponse>> methods() {
        return ApiResponse.ok(paymentService.availableMethods());
    }

    /**
     * Public: ABA PayWay's pushback after a card payment (JSON, or form-encoded with
     * {@code tran_id} or a JSON {@code response} field). Only {@code tran_id} is read - the
     * outcome is fetched from PayWay itself, so a forged call can at worst trigger a check.
     */
    @PostMapping("/payway/callback")
    public ApiResponse<Map<String, Boolean>> paywayCallback(HttpServletRequest request)
            throws IOException {
        String tranId = callbackTranId(request);
        if (tranId == null) {
            throw new BadRequestException("tran_id is required");
        }
        if (SAFE_TRAN_ID.matcher(tranId).matches()) {
            paymentService.handleCardCallback(tranId);
        } else {
            log.info("Card callback with an unusable tran_id ignored");
        }
        return ApiResponse.ok(Map.of("received", true));
    }

    private String callbackTranId(HttpServletRequest request) throws IOException {
        String contentType = request.getContentType() == null
                ? "" : request.getContentType().toLowerCase(Locale.ROOT);
        String tranId;
        if (contentType.startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                || contentType.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)) {
            tranId = request.getParameter("tran_id");
            if (isBlank(tranId)) {
                tranId = tranIdFromJson(request.getParameter("response"));
            }
        } else {
            byte[] body = request.getInputStream().readNBytes(MAX_CALLBACK_BODY);
            tranId = tranIdFromJson(new String(body, StandardCharsets.UTF_8));
        }
        if (isBlank(tranId)) {
            tranId = request.getParameter("tran_id");   // query string, as a last resort
        }
        return isBlank(tranId) ? null : tranId.trim();
    }

    private String tranIdFromJson(String text) {
        if (isBlank(text)) {
            return null;
        }
        try {
            JsonNode value = objectMapper.readTree(text).path("tran_id");
            return value.isValueNode() && !value.isNull() ? value.asString() : null;
        } catch (JacksonException ex) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Start paying an order. For KHQR the response carries {@code data.khqr.qrImage}
     * (a PNG data URI) and {@code data.khqr.md5}. For CARD it carries
     * {@code data.cardCheckout} - the form to post to the card provider's hosted page, and
     * {@code display} ("popup" or "redirect") saying whether to show that page in a popup
     * over the shop or in the same tab.
     */
    @PostMapping("/orders/{orderId}")
    public ApiResponse<PaymentResponse> pay(@AuthenticationPrincipal CustomUserDetails principal,
                                            @PathVariable Long orderId) {
        return ApiResponse.ok("Payment started", paymentService.pay(principal.getId(), orderId));
    }

    /**
     * Poll after showing the QR. Returns the payment with {@code paymentStatus} flipped to
     * PAID once Bakong confirms; the order moves to CONFIRMED at the same moment.
     */
    @PostMapping("/{id}/check")
    public ApiResponse<PaymentResponse> checkStatus(
            @AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long id) {
        return ApiResponse.ok(paymentService.checkStatus(principal.getId(), id));
    }

    @GetMapping
    public ApiResponse<PageResponse<PaymentResponse>> listOwn(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(paymentService.listOwn(principal.getId(), pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<PaymentResponse> getOwn(
            @AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long id) {
        return ApiResponse.ok(paymentService.getOwn(principal.getId(), id));
    }
}

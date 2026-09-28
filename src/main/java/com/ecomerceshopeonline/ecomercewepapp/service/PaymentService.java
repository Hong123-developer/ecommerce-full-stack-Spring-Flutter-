package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.VerifyPaymentRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PaymentResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentStatus;
import org.springframework.data.domain.Pageable;

public interface PaymentService {

    /**
     * Starts payment for an order the shopper owns. For KHQR this returns the QR payload,
     * a ready-to-render PNG and the MD5 used to poll for settlement; other methods just
     * return the pending record.
     */
    PaymentResponse pay(Long userId, Long orderId);

    /**
     * Asks Bakong whether the QR has been paid and settles the order if so.
     * Bakong offers no webhook, so the frontend polls this every few seconds.
     */
    PaymentResponse checkStatus(Long userId, Long paymentId);

    PageResponse<PaymentResponse> listOwn(Long userId, Pageable pageable);

    PaymentResponse getOwn(Long userId, Long paymentId);

    // --- admin ---

    PageResponse<PaymentResponse> adminList(PaymentStatus status, String search, Pageable pageable);

    PaymentResponse adminGet(Long paymentId);

    /** Manual confirmation, for cash on delivery or a bank transfer seen on a statement. */
    PaymentResponse adminVerify(Long paymentId, VerifyPaymentRequest request);

    /** Re-checks a KHQR payment against Bakong on the administrator's behalf. */
    PaymentResponse adminCheckWithBakong(Long paymentId);
}

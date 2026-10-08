package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.VerifyPaymentRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PaymentMethodResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PaymentResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentStatus;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentService {

    /**
     * Starts payment for an order the shopper owns. For KHQR this returns the QR payload,
     * a ready-to-render PNG and the MD5 used to poll for settlement. For CARD it starts a
     * new attempt and returns {@code cardCheckout}, the signed form for the provider's hosted
     * page (or the PAID payment if the previous attempt turns out to be paid). Other methods
     * just return the pending record.
     */
    PaymentResponse pay(Long userId, Long orderId);

    /**
     * Asks the provider (Bakong for KHQR, the card provider for CARD) whether the payment
     * has gone through and settles the order if so. The frontend polls this every few seconds.
     */
    PaymentResponse checkStatus(Long userId, Long paymentId);

    /** Which methods checkout may offer right now (public). */
    List<PaymentMethodResponse> availableMethods();

    /**
     * The card provider's pushback for one attempt. Only the id is used: the outcome is
     * fetched from the provider itself. Unknown ids are ignored; never throws.
     */
    void handleCardCallback(String tranId);

    /**
     * Releases unpaid card orders whose latest attempt is older than the attempt lifetime
     * (plus grace): EXPIRED, stock back. Each payment is handled in its own transaction.
     *
     * @return how many were released
     */
    int expireStaleCardPayments(LocalDateTime now);

    PageResponse<PaymentResponse> listOwn(Long userId, Pageable pageable);

    PaymentResponse getOwn(Long userId, Long paymentId);

    // --- admin ---

    PageResponse<PaymentResponse> adminList(PaymentStatus status, String search, Pageable pageable);

    PaymentResponse adminGet(Long paymentId);

    /** Manual confirmation, for cash on delivery or a bank transfer seen on a statement. */
    PaymentResponse adminVerify(Long paymentId, VerifyPaymentRequest request);

    /**
     * Re-checks a payment with its provider on the administrator's behalf: Bakong for KHQR,
     * the card provider for CARD.
     */
    PaymentResponse adminCheckWithBakong(Long paymentId);
}

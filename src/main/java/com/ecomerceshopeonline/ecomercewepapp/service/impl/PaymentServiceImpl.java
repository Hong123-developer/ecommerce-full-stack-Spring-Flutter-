package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.config.AppProperties;
import com.ecomerceshopeonline.ecomercewepapp.dto.request.VerifyPaymentRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PaymentMethodResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PaymentResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.Order;
import com.ecomerceshopeonline.ecomercewepapp.entity.Payment;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentMethod;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentStatus;
import com.ecomerceshopeonline.ecomercewepapp.exception.ApiException;
import com.ecomerceshopeonline.ecomercewepapp.exception.BadRequestException;
import com.ecomerceshopeonline.ecomercewepapp.exception.ResourceNotFoundException;
import com.ecomerceshopeonline.ecomercewepapp.mapper.PaymentMapper;
import com.ecomerceshopeonline.ecomercewepapp.repository.OrderRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.PaymentRepository;
import com.ecomerceshopeonline.ecomercewepapp.service.BakongClient;
import com.ecomerceshopeonline.ecomercewepapp.service.CardPaymentGateway;
import com.ecomerceshopeonline.ecomercewepapp.service.CardPaymentGateway.CardTransactionResult;
import com.ecomerceshopeonline.ecomercewepapp.service.NotificationService;
import com.ecomerceshopeonline.ecomercewepapp.service.OrderService;
import com.ecomerceshopeonline.ecomercewepapp.service.PaymentService;
import com.ecomerceshopeonline.ecomercewepapp.util.BankNameResolver;
import com.ecomerceshopeonline.ecomercewepapp.util.KhqrGenerator;
import com.ecomerceshopeonline.ecomercewepapp.util.QrImageRenderer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentMapper paymentMapper;
    private final KhqrGenerator khqrGenerator;
    private final QrImageRenderer qrImageRenderer;
    private final BankNameResolver bankNameResolver;
    private final BakongClient bakongClient;
    private final AppProperties properties;
    private final CardPaymentGateway cardGateway;
    private final OrderService orderService;
    private final NotificationService notificationService;
    private final PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    /** Card brands the hosted page takes - virtual cards of these brands work the same. */
    private static final List<String> CARD_BRANDS = List.of("VISA", "MASTERCARD", "UNIONPAY", "JCB");

    /** Room past an attempt's lifetime before the order is released (3-D Secure in flight). */
    private static final Duration CARD_GRACE = Duration.ofMinutes(5);

    /** A card payment that still waits for money; FAILED may be retried with a new attempt. */
    private static final EnumSet<PaymentStatus> CARD_UNPAID =
            EnumSet.of(PaymentStatus.PENDING, PaymentStatus.FAILED);

    /**
     * Attempt id = order id + the last 10 digits of the epoch millis. Digits only (the
     * provider's examples are), at most 20 characters, unique per attempt, and the fixed-width
     * suffix lets a late callback for an older attempt be traced back to its order.
     */
    private static final int TRAN_SUFFIX_DIGITS = 10;
    private static final long TRAN_SUFFIX_MODULO = 10_000_000_000L;
    private static final int MAX_TRAN_ID = 20;
    private static final Pattern OUR_TRAN_ID = Pattern.compile("\\d{" + (TRAN_SUFFIX_DIGITS + 1) + "," + MAX_TRAN_ID + "}");

    // ---------------------------------------------------------------- customer

    @Override
    @Transactional
    public PaymentResponse pay(Long userId, Long orderId) {
        Payment payment = requireOwnPaymentForOrder(userId, orderId);

        if (payment.getPaymentMethod() == PaymentMethod.CARD) {
            return payByCard(payment);
        }
        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new BadRequestException(
                    "This order is already " + payment.getPaymentStatus().name().toLowerCase());
        }
        if (payment.getPaymentMethod() != PaymentMethod.KHQR) {
            // Cash on delivery and bank transfer settle out of band; nothing to mint here.
            return paymentMapper.toResponse(payment, false);
        }
        PaymentResponse.Khqr khqr = issueOrReuseQr(payment);
        // null means the expired QR turned out to be paid; the payment is now PAID.
        return paymentMapper.toResponse(payment, false, khqr);
    }

    @Override
    @Transactional
    public PaymentResponse checkStatus(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findByIdAndUserIdWithOrder(paymentId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Payment", paymentId));
        if (payment.getPaymentMethod() == PaymentMethod.CARD) {
            return paymentMapper.toResponse(pollCard(payment), false);
        }
        return paymentMapper.toResponse(pollBakong(payment), false);
    }

    @Override
    public List<PaymentMethodResponse> availableMethods() {
        boolean khqr = properties.bakong() != null && properties.bakong().isConfigured();
        boolean card = cardGateway.isConfigured();
        return List.of(
                PaymentMethodResponse.of(PaymentMethod.KHQR.name(), khqr),
                PaymentMethodResponse.of(PaymentMethod.CASH_ON_DELIVERY.name(), true),
                new PaymentMethodResponse(PaymentMethod.CARD.name(), card,
                        card ? cardGateway.provider() : null, CARD_BRANDS));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> listOwn(Long userId, Pageable pageable) {
        return PageResponse.from(paymentRepository.findByUserWithOrder(userId, pageable),
                payment -> paymentMapper.toResponse(payment, false));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getOwn(Long userId, Long paymentId) {
        return paymentMapper.toResponse(
                paymentRepository.findByIdAndUserIdWithOrder(paymentId, userId)
                        .orElseThrow(() -> ResourceNotFoundException.of("Payment", paymentId)),
                false);
    }

    // ---------------------------------------------------------------- admin

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> adminList(PaymentStatus status, String search,
                                                   Pageable pageable) {
        return PageResponse.from(
                paymentRepository.adminSearch(status, blankToNull(search), pageable),
                payment -> paymentMapper.toResponse(payment, true));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse adminGet(Long paymentId) {
        return paymentMapper.toResponse(requireDetail(paymentId), true);
    }

    @Override
    @Transactional
    public PaymentResponse adminVerify(Long paymentId, VerifyPaymentRequest request) {
        Payment payment = requireDetail(paymentId);
        requireSettleable(payment);

        String transactionId = request == null ? null : blankToNull(request.transactionId());
        settle(payment, transactionId, false);
        log.info("Payment {} manually verified by an administrator", paymentId);
        return paymentMapper.toResponse(payment, true);
    }

    @Override
    @Transactional
    public PaymentResponse adminCheckWithBakong(Long paymentId) {
        Payment payment = requireDetail(paymentId);
        if (payment.getPaymentMethod() == PaymentMethod.CARD) {
            return paymentMapper.toResponse(pollCard(payment), true);
        }
        return paymentMapper.toResponse(pollBakong(payment), true);
    }

    // ---------------------------------------------------------------- CARD

    /**
     * Starts a new card attempt. If an earlier attempt exists it is asked about first, so a
     * payment made just before the shopper came back is settled rather than paid twice.
     */
    private PaymentResponse payByCard(Payment payment) {
        lockPayment(payment);
        if (!CARD_UNPAID.contains(payment.getPaymentStatus())) {
            throw new BadRequestException(
                    "This order is already " + payment.getPaymentStatus().name().toLowerCase());
        }
        if (!cardGateway.isConfigured()) {
            throw new ApiException("Card payment is not available yet", HttpStatus.SERVICE_UNAVAILABLE);
        }

        String previous = payment.getGatewayTranId();
        if (previous != null) {
            try {
                applyCardResult(payment, previous, cardGateway.check(previous));
            } catch (ApiException ex) {
                // The provider being unreachable must not block a new attempt.
                log.warn("Could not re-check card attempt {} of payment {} before a new one: {}",
                        previous, payment.getId(), ex.getMessage());
            }
            if (payment.getPaymentStatus() == PaymentStatus.PAID) {
                return paymentMapper.toResponse(payment, false);
            }
        }
        if (cardWindowClosed(payment, LocalDateTime.now())) {
            expireCard(payment);
            return paymentMapper.toResponse(payment, false);
        }

        String tranId = newTranId(payment);
        payment.setGatewayProvider(cardGateway.provider());
        payment.setGatewayTranId(tranId);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        paymentRepository.save(payment);

        Order order = payment.getOrder();
        CardPaymentGateway.CardCheckout checkout = cardGateway.createCheckout(order, payment, tranId);
        log.info("Card attempt {} started for order {} via {}",
                tranId, order.getOrderNumber(), cardGateway.provider());
        return paymentMapper.toResponse(payment, false, null,
                new PaymentResponse.CardCheckout(checkout.action(), checkout.method(),
                        checkout.display(), checkout.fields()));
    }

    /**
     * The shopper's poll (and the admin's "check"): asks the provider about the latest attempt
     * and settles, marks it FAILED, or - once the window is over - releases the order.
     */
    private Payment pollCard(Payment payment) {
        if (!CARD_UNPAID.contains(payment.getPaymentStatus())) {
            return payment;
        }
        String tranId = payment.getGatewayTranId();
        if (tranId != null) {
            if (!cardGateway.isConfigured()) {
                throw new ApiException("Card payment is not available yet", HttpStatus.SERVICE_UNAVAILABLE);
            }
            CardTransactionResult result = cardGateway.check(tranId);
            lockPayment(payment);
            applyCardResult(payment, tranId, result);
        } else {
            lockPayment(payment);
        }
        if (CARD_UNPAID.contains(payment.getPaymentStatus())
                && cardWindowClosed(payment, LocalDateTime.now())) {
            expireCard(payment);
        }
        return payment;
    }

    @Override
    @Transactional
    public void handleCardCallback(String tranId) {
        try {
            if (!cardGateway.isConfigured()) {
                log.warn("Card callback for tran {} ignored: card payments are not configured", tranId);
                return;
            }
            Payment payment = paymentRepository.findByGatewayTranIdWithOrder(tranId)
                    .or(() -> supersededAttemptOwner(tranId))
                    .orElse(null);
            if (payment == null) {
                log.info("Card callback for unknown tran {} ignored", tranId);
                return;
            }
            if (payment.getPaymentStatus() == PaymentStatus.PAID) {
                return;   // a repeat - already settled
            }
            CardTransactionResult result = cardGateway.check(tranId);
            lockPayment(payment);
            applyCardResult(payment, tranId, result);
        } catch (ApiException ex) {
            // The shopper's poll or the expiry sweep will ask again.
            log.warn("Card callback for tran {} could not be confirmed now: {}", tranId, ex.getMessage());
        }
    }

    @Override
    public int expireStaleCardPayments(LocalDateTime now) {
        LocalDateTime cutoff = now.minusMinutes(cardGateway.attemptLifetimeMinutes()).minus(CARD_GRACE);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        List<Long> stale = tx.execute(status -> paymentRepository.findStaleIds(
                PaymentMethod.CARD, CARD_UNPAID, OrderStatus.PENDING, cutoff));
        int released = 0;
        for (Long paymentId : stale == null ? List.<Long>of() : stale) {
            try {
                if (Boolean.TRUE.equals(tx.execute(status -> expireIfStale(paymentId, now)))) {
                    released++;
                }
            } catch (RuntimeException ex) {
                // e.g. the provider is unreachable: the attempt might be paid - try next round.
                log.warn("Could not expire card payment {} yet: {}", paymentId, ex.getMessage());
            }
        }
        return released;
    }

    private boolean expireIfStale(Long paymentId, LocalDateTime now) {
        Payment payment = paymentRepository.findDetailById(paymentId).orElse(null);
        if (payment == null || payment.getPaymentMethod() != PaymentMethod.CARD) {
            return false;
        }
        String tranId = payment.getGatewayTranId();
        if (tranId != null) {
            if (!cardGateway.isConfigured()) {
                return false;   // cannot rule out that the attempt was paid
            }
            CardTransactionResult result = cardGateway.check(tranId);
            lockPayment(payment);
            applyCardResult(payment, tranId, result);
        } else {
            lockPayment(payment);
        }
        if (!CARD_UNPAID.contains(payment.getPaymentStatus()) || !cardWindowClosed(payment, now)) {
            return false;
        }
        return expireCard(payment);
    }

    /**
     * Acts on the provider's verdict for one attempt. The payment must be locked. Settles only
     * an approval for exactly the order total; never resurrects a cancelled or expired order.
     */
    private void applyCardResult(Payment payment, String tranId, CardTransactionResult result) {
        switch (result.status()) {
            case PAID -> settleCard(payment, tranId, result);
            case FAILED -> {
                // Only the current attempt; a stale verdict on an older one changes nothing.
                if (payment.getPaymentStatus() == PaymentStatus.PENDING
                        && tranId.equals(payment.getGatewayTranId())) {
                    payment.setPaymentStatus(PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                    log.info("Card attempt {} of payment {} failed ({})",
                            tranId, payment.getId(), result.rawStatus());
                }
            }
            default -> {
                // PENDING / NOT_FOUND: nothing happened yet.
            }
        }
    }

    private void settleCard(Payment payment, String tranId, CardTransactionResult result) {
        if (payment.getPaymentStatus() == PaymentStatus.PAID) {
            return;   // settled already (duplicate callback, or the poll got there first)
        }
        Order order = payment.getOrder();
        if (!amountMatches(payment, result)) {
            log.warn("Card tran {} of payment {} (order {}) was approved for a different amount or"
                            + " currency than the order - NOT settled, check it in the {} portal",
                    tranId, payment.getId(), order.getOrderNumber(), cardGateway.provider());
            return;
        }
        if (!CARD_UNPAID.contains(payment.getPaymentStatus())) {
            // Money arrived for an order that is no longer payable (cancelled / expired).
            log.warn("Card tran {} paid but payment {} is {} (order {}) - needs a manual refund in the {} portal",
                    tranId, payment.getId(), payment.getPaymentStatus(), order.getOrderNumber(),
                    cardGateway.provider());
            return;
        }
        payment.setGatewayProvider(cardGateway.provider());
        // The attempt that was actually paid - possibly an older one than the latest.
        payment.setGatewayTranId(tranId);
        payment.setGatewayApprovalCode(truncate(result.approvalCode(), 40));
        payment.setCardMasked(truncate(result.cardMasked(), 40));
        settle(payment, tranId);
        log.info("Payment {} confirmed by {} (tran {})", payment.getId(), cardGateway.provider(), tranId);
    }

    /** Exactly the order total from the database, in the payment's currency. */
    private boolean amountMatches(Payment payment, CardTransactionResult result) {
        if (result.amount() == null || result.currency() == null) {
            return false;
        }
        int scale = "KHR".equalsIgnoreCase(payment.getCurrency()) ? 0 : 2;
        BigDecimal expected = payment.getOrder().getTotalAmount().setScale(scale, RoundingMode.HALF_UP);
        return result.amount().compareTo(expected) == 0
                && result.currency().trim().equalsIgnoreCase(payment.getCurrency());
    }

    /** The payment window restarts with every attempt (each save of the payment). */
    private boolean cardWindowClosed(Payment payment, LocalDateTime now) {
        LocalDateTime lastActivity = payment.getUpdatedAt() != null
                ? payment.getUpdatedAt() : payment.getCreatedAt();
        return lastActivity != null && lastActivity
                .plusMinutes(cardGateway.attemptLifetimeMinutes())
                .plus(CARD_GRACE)
                .isBefore(now);
    }

    /** EXPIRED + stock back, through the same code path as a cancellation. */
    private boolean expireCard(Payment payment) {
        entityManager.flush();
        return orderService.expireUnpaidOrder(payment.getOrder().getId());
    }

    private String newTranId(Payment payment) {
        String orderPart = String.valueOf(payment.getOrder().getId());
        long millis = System.currentTimeMillis();
        String tranId;
        do {
            tranId = orderPart + String.format("%0" + TRAN_SUFFIX_DIGITS + "d", millis % TRAN_SUFFIX_MODULO);
            millis++;
        } while (tranId.equals(payment.getGatewayTranId()));
        if (tranId.length() > MAX_TRAN_ID) {
            throw new IllegalStateException("Order id too long for a card attempt id: " + orderPart);
        }
        return tranId;
    }

    /**
     * A callback for an attempt that is no longer the latest one (the shopper started a new
     * attempt, then completed the old page after all). Our ids start with the order id; the
     * attempt must have been minted while this payment existed.
     */
    private Optional<Payment> supersededAttemptOwner(String tranId) {
        if (!OUR_TRAN_ID.matcher(tranId).matches()) {
            return Optional.empty();
        }
        long orderId;
        try {
            orderId = Long.parseLong(tranId.substring(0, tranId.length() - TRAN_SUFFIX_DIGITS));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
        long suffix = Long.parseLong(tranId.substring(tranId.length() - TRAN_SUFFIX_DIGITS));
        return paymentRepository.findByOrderIdWithOrder(orderId)
                .filter(payment -> payment.getPaymentMethod() == PaymentMethod.CARD)
                .filter(payment -> payment.getGatewayTranId() != null)
                .filter(payment -> mintedDuring(payment, suffix));
    }

    private boolean mintedDuring(Payment payment, long suffix) {
        if (payment.getCreatedAt() == null) {
            return false;
        }
        long slack = Duration.ofMinutes(1).toMillis();
        long created = payment.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long minted = created - created % TRAN_SUFFIX_MODULO + suffix;
        if (minted < created - slack) {
            minted += TRAN_SUFFIX_MODULO;
        }
        return minted <= System.currentTimeMillis() + slack;
    }

    /**
     * Re-reads the payment under a row lock, so the shopper's poll, the provider's callback,
     * a new attempt and a cancellation can never overwrite each other's outcome.
     */
    private void lockPayment(Payment payment) {
        entityManager.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    // ---------------------------------------------------------------- KHQR

    /**
     * Reuses the stored QR while it is still valid. Bakong keys a payment by the MD5 of the
     * exact payload, so re-minting a QR that the shopper already scanned would orphan it.
     */
    private PaymentResponse.Khqr issueOrReuseQr(Payment payment) {
        AppProperties.Bakong config = properties.bakong();
        if (!config.isConfigured()) {
            throw new ApiException(
                    "KHQR is not configured yet. Set app.bakong.account-id to your Bakong account.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        String payload = payment.getKhqr();
        boolean stillValid = payload != null && khqrGenerator.expiryOf(payload)
                .map(expiry -> expiry.isAfter(Instant.now()))
                .orElse(false);

        if (!stillValid && payload != null && lastChanceCheck(payment)) {
            // The shopper paid the old QR just before it expired - nothing new to mint.
            return null;
        }

        if (!stillValid) {
            KhqrGenerator.Khqr generated = khqrGenerator.generate(
                    payment.getAmount(), payment.getCurrency(),
                    payment.getOrder().getOrderNumber());
            payload = generated.payload();
            payment.setKhqr(payload);
            paymentRepository.save(payment);
            log.info("Issued KHQR for order {} (expires {})",
                    payment.getOrder().getOrderNumber(), generated.expiresAt());
        }

        Instant expiresAt = khqrGenerator.expiryOf(payload).orElse(null);
        long expiresInSeconds = expiresAt == null
                ? 0
                : Math.max(0, Duration.between(Instant.now(), expiresAt).getSeconds());
        return new PaymentResponse.Khqr(
                payload,
                khqrGenerator.md5(payload),
                qrImageRenderer.toDataUri(payload),
                bankNameResolver.resolve(config.accountId()),
                config.accountId(),
                expiresAt,
                expiresInSeconds);
    }

    /**
     * Before replacing an expired QR, asks Bakong once more about the old one, so a payment
     * made in its final seconds is settled rather than orphaned under a new md5.
     * Bakong being unreachable must not block issuing a fresh QR, so failures are logged.
     *
     * @return true if the old QR turned out to be paid (the payment is now PAID)
     */
    private boolean lastChanceCheck(Payment payment) {
        try {
            return pollBakong(payment).getPaymentStatus() == PaymentStatus.PAID;
        } catch (ApiException ex) {
            log.warn("Could not re-check expired KHQR for payment {} before re-issuing: {}",
                    payment.getId(), ex.getMessage());
            return false;
        }
    }

    /** Polls Bakong and settles the payment if the money has landed. */
    private Payment pollBakong(Payment payment) {
        if (payment.getPaymentStatus() == PaymentStatus.PAID) {
            return payment;
        }
        if (payment.getPaymentMethod() != PaymentMethod.KHQR) {
            throw new BadRequestException("Only KHQR payments can be checked with Bakong");
        }
        if (payment.getKhqr() == null) {
            throw new BadRequestException("No KHQR has been issued for this order yet");
        }

        BakongClient.TransactionCheck check =
                bakongClient.checkTransaction(khqrGenerator.md5(payment.getKhqr()));

        if (check.paid() && payment.getPaymentStatus() != PaymentStatus.PENDING) {
            // Money arrived for an order that is no longer payable (e.g. cancelled while the
            // QR was open). Do not resurrect it as PAID - flag it for a manual refund.
            log.warn("Bakong reports payment {} paid but it is {} (order {}) - needs manual refund, tx {}",
                    payment.getId(), payment.getPaymentStatus(),
                    payment.getOrder().getOrderNumber(), check.transactionHash());
            return payment;
        }

        if (check.paid()) {
            settle(payment, check.transactionHash());
            log.info("Payment {} confirmed by Bakong (hash {})",
                    payment.getId(), check.transactionHash());
        }
        return payment;
    }

    // ---------------------------------------------------------------- helpers

    /** Marks the payment paid and moves the order out of PENDING; the admins hear about it. */
    private void settle(Payment payment, String transactionId) {
        settle(payment, transactionId, true);
    }

    /**
     * @param notifyAdmins false when an admin settles it by hand - they already know; the
     *                     customer is always told
     */
    private void settle(Payment payment, String transactionId, boolean notifyAdmins) {
        payment.setPaymentStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        if (transactionId != null && !transactionId.isBlank()) {
            payment.setTransactionId(transactionId);
        }
        paymentRepository.save(payment);

        Order order = payment.getOrder();
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);
        }
        notificationService.paymentReceived(order, notifyAdmins);
    }

    private void requireSettleable(Payment payment) {
        if (payment.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BadRequestException("This payment is already verified");
        }
        if (payment.getPaymentStatus() == PaymentStatus.CANCELLED) {
            throw new BadRequestException("This payment was cancelled and cannot be verified");
        }
    }

    private Payment requireOwnPaymentForOrder(Long userId, Long orderId) {
        Payment payment = paymentRepository.findByOrderIdWithOrder(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Payment for order", orderId));
        // 404 rather than 403: another shopper's order should not be confirmed to exist.
        if (!payment.getUser().getId().equals(userId)) {
            throw ResourceNotFoundException.of("Payment for order", orderId);
        }
        return payment;
    }

    private Payment requireDetail(Long paymentId) {
        return paymentRepository.findDetailById(paymentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Payment", paymentId));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

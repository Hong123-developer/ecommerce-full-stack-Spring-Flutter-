package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.config.AppProperties;
import com.ecomerceshopeonline.ecomercewepapp.dto.request.VerifyPaymentRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
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
import com.ecomerceshopeonline.ecomercewepapp.service.PaymentService;
import com.ecomerceshopeonline.ecomercewepapp.util.BankNameResolver;
import com.ecomerceshopeonline.ecomercewepapp.util.KhqrGenerator;
import com.ecomerceshopeonline.ecomercewepapp.util.QrImageRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

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

    // ---------------------------------------------------------------- customer

    @Override
    @Transactional
    public PaymentResponse pay(Long userId, Long orderId) {
        Payment payment = requireOwnPaymentForOrder(userId, orderId);

        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new BadRequestException(
                    "This order is already " + payment.getPaymentStatus().name().toLowerCase());
        }
        if (payment.getPaymentMethod() != PaymentMethod.KHQR) {
            // Cash on delivery and bank transfer settle out of band; nothing to mint here.
            return paymentMapper.toResponse(payment, false);
        }
        return paymentMapper.toResponse(payment, false, issueOrReuseQr(payment));
    }

    @Override
    @Transactional
    public PaymentResponse checkStatus(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findByIdAndUserIdWithOrder(paymentId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Payment", paymentId));
        return paymentMapper.toResponse(pollBakong(payment), false);
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
        settle(payment, transactionId);
        log.info("Payment {} manually verified by an administrator", paymentId);
        return paymentMapper.toResponse(payment, true);
    }

    @Override
    @Transactional
    public PaymentResponse adminCheckWithBakong(Long paymentId) {
        return paymentMapper.toResponse(pollBakong(requireDetail(paymentId)), true);
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

        return new PaymentResponse.Khqr(
                payload,
                khqrGenerator.md5(payload),
                qrImageRenderer.toDataUri(payload),
                bankNameResolver.resolve(config.accountId()),
                config.accountId(),
                khqrGenerator.expiryOf(payload).map(this::toLocalDateTime).orElse(null));
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

        if (check.paid()) {
            settle(payment, check.transactionHash());
            log.info("Payment {} confirmed by Bakong (hash {})",
                    payment.getId(), check.transactionHash());
        }
        return payment;
    }

    // ---------------------------------------------------------------- helpers

    /** Marks the payment paid and moves the order out of PENDING. */
    private void settle(Payment payment, String transactionId) {
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

    private LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

package com.ecomerceshopeonline.ecomercewepapp.mapper;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.OrderResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PaymentResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.Payment;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    /** Without QR details - used for history and admin listings. */
    public PaymentResponse toResponse(Payment payment, boolean includeCustomer) {
        return toResponse(payment, includeCustomer, null);
    }

    public PaymentResponse toResponse(Payment payment, boolean includeCustomer,
                                      PaymentResponse.Khqr khqr) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getOrder().getOrderNumber(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentMethod(),
                payment.getPaymentStatus(),
                payment.getTransactionId(),
                khqr,
                payment.getPaidAt(),
                includeCustomer ? toCustomer(payment.getUser()) : null,
                payment.getCreatedAt());
    }

    private OrderResponse.CustomerSummary toCustomer(User user) {
        return new OrderResponse.CustomerSummary(
                user.getId(), user.getFullName(), user.getEmail(), user.getPhone());
    }
}

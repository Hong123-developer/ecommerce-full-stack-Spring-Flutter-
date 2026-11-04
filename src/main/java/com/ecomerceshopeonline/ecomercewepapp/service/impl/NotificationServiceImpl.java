package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.NotificationResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.Notification;
import com.ecomerceshopeonline.ecomercewepapp.entity.Order;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.DeliveryType;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.NotificationType;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentMethod;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.RoleName;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.ShipmentStage;
import com.ecomerceshopeonline.ecomercewepapp.exception.ResourceNotFoundException;
import com.ecomerceshopeonline.ecomercewepapp.repository.NotificationRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.UserRepository;
import com.ecomerceshopeonline.ecomercewepapp.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    // ---------------------------------------------------------------- order events

    @Override
    @Transactional
    public void orderPlaced(Order order) {
        String title = "New order #" + order.getOrderNumber();
        String message = "%s ordered %s · %s".formatted(
                customerName(order), money(order.getTotalAmount()), paymentPhrase(order));
        toAdmins(order, NotificationType.NEW_ORDER, title, message);
    }

    @Override
    @Transactional
    public void paymentReceived(Order order, boolean notifyAdmins) {
        if (notifyAdmins) {
            toAdmins(order, NotificationType.PAYMENT_RECEIVED,
                    "Payment received for #" + order.getOrderNumber(),
                    "%s paid %s online (%s).".formatted(
                            customerName(order), money(order.getTotalAmount()), methodName(order)));
        }
        toCustomer(order, NotificationType.PAYMENT_RECEIVED,
                "Payment received",
                "We received your payment of %s for order #%s. Your order is confirmed."
                        .formatted(money(order.getTotalAmount()), order.getOrderNumber()));
    }

    @Override
    @Transactional
    public void orderCancelledByCustomer(Order order) {
        toAdmins(order, NotificationType.ORDER_CANCELLED,
                "Order #" + order.getOrderNumber() + " cancelled",
                "%s cancelled their order (%s). The items are back in stock."
                        .formatted(customerName(order), money(order.getTotalAmount())));
    }

    @Override
    @Transactional
    public void orderStatusChanged(Order order) {
        String number = order.getOrderNumber();
        boolean pickup = order.getDeliveryType() == DeliveryType.PICKUP;
        boolean payAtCounter = payment(order) == PaymentMethod.CASH_ON_DELIVERY;
        String title;
        String message;
        switch (order.getStatus()) {
            case CONFIRMED -> {
                title = "Order confirmed";
                message = pickup && payAtCounter
                        ? "Order #%s is confirmed. We'll tell you when it's ready - pay in cash when you collect it."
                        .formatted(number)
                        : "Order #%s is confirmed. We're getting it ready.".formatted(number);
            }
            case PROCESSING -> {
                title = "Preparing your order";
                message = "We're preparing order #%s.".formatted(number);
            }
            case SHIPPED -> {
                title = pickup ? "Ready for pickup" : "Order on its way";
                message = pickup
                        ? "Order #%s is ready. Bring your order number to the store%s."
                        .formatted(number, payAtCounter ? " and pay in cash there" : "")
                        : "Order #%s has been shipped and is on its way to you.".formatted(number);
            }
            case DELIVERED -> {
                title = pickup ? "Order collected" : "Order delivered";
                message = "Order #%s is complete. Thank you for shopping with us!".formatted(number);
            }
            case CANCELLED -> {
                title = "Order cancelled";
                message = "Order #%s has been cancelled by the store.".formatted(number);
            }
            case REFUNDED -> {
                title = "Order refunded";
                message = "Order #%s has been refunded.".formatted(number);
            }
            default -> {
                // Back to PENDING (or anything new): nothing a shopper needs to hear about.
                return;
            }
        }
        toCustomer(order, NotificationType.ORDER_UPDATED, title, message);
    }

    @Override
    @Transactional
    public void shipmentStageChanged(Order order) {
        ShipmentStage stage = order.getShipmentStage();
        if (stage == null) {
            return;
        }
        String number = order.getOrderNumber();
        String title;
        String message;
        switch (stage) {
            case PICKED_UP -> {
                title = "Picked up by courier";
                message = "Order #%s has been picked up by the courier.".formatted(number);
            }
            case IN_TRANSIT -> {
                title = "Order in transit";
                message = "Order #%s is in transit.".formatted(number);
            }
            case OUT_FOR_DELIVERY -> {
                title = "Out for delivery";
                message = "Order #%s is out for delivery and will arrive soon.".formatted(number);
            }
            default -> {
                return;
            }
        }
        toCustomer(order, NotificationType.ORDER_UPDATED, title, message);
    }

    @Override
    @Transactional
    public void orderExpired(Order order) {
        toCustomer(order, NotificationType.ORDER_EXPIRED,
                "Order cancelled - not paid",
                "Order #%s was cancelled because the payment wasn't completed in time."
                        .formatted(order.getOrderNumber()));
    }

    // ---------------------------------------------------------------- own notifications

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listOwn(Long userId, int page, int size) {
        PageRequest request = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return PageResponse.from(notificationRepository.findOwn(userId, request), NotificationResponse::of);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Override
    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        // 404 for someone else's notification too - its existence is none of this user's business.
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", notificationId));
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }
        return NotificationResponse.of(notification);
    }

    @Override
    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllRead(userId, LocalDateTime.now());
    }

    // ---------------------------------------------------------------- helpers

    private void toAdmins(Order order, NotificationType type, String title, String message) {
        List<User> admins = userRepository.findEnabledByRole(RoleName.ROLE_ADMIN);
        if (admins.isEmpty()) {
            log.warn("No enabled administrator to notify about order {}", order.getOrderNumber());
            return;
        }
        notificationRepository.saveAll(admins.stream()
                .map(admin -> build(admin, order, type, title, message,
                        "/admin/orders/" + order.getId(), customerName(order)))
                .toList());
    }

    private void toCustomer(Order order, NotificationType type, String title, String message) {
        notificationRepository.save(
                build(order.getUser(), order, type, title, message, "/orders/" + order.getId(), null));
    }

    private Notification build(User recipient, Order order, NotificationType type, String title,
                               String message, String link, String actorName) {
        return Notification.builder()
                .user(recipient)
                .type(type)
                .title(truncate(title, 160))
                .message(truncate(message, 500))
                .link(link)
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .orderStatus(order.getStatus())
                .paymentMethod(payment(order))
                .deliveryType(order.getDeliveryType())
                .amount(order.getTotalAmount())
                .actorName(actorName == null ? null : truncate(actorName, 120))
                .build();
    }

    private static PaymentMethod payment(Order order) {
        return order.getPayment() == null ? null : order.getPayment().getPaymentMethod();
    }

    /** The name the store knows the order by: the pickup/delivery contact, else the account. */
    private static String customerName(Order order) {
        if (order.getContactName() != null && !order.getContactName().isBlank()) {
            return order.getContactName().trim();
        }
        User user = order.getUser();
        return user == null || user.getFullName() == null ? "A customer" : user.getFullName();
    }

    /** How the store gets its money, in the words an admin scanning the bell wants. */
    private static String paymentPhrase(Order order) {
        PaymentMethod method = payment(order);
        if (method == null) {
            return "payment not chosen";
        }
        return switch (method) {
            case CASH_ON_DELIVERY -> order.getDeliveryType() == DeliveryType.PICKUP
                    ? "pays cash at the store"
                    : "pays cash on delivery";
            case KHQR -> "paying online (KHQR)";
            case CARD -> "paying online (card)";
            case BANK_TRANSFER -> "paying by bank transfer";
        };
    }

    private static String methodName(Order order) {
        PaymentMethod method = payment(order);
        if (method == null) {
            return "online";
        }
        return switch (method) {
            case KHQR -> "KHQR";
            case CARD -> "card";
            case BANK_TRANSFER -> "bank transfer";
            case CASH_ON_DELIVERY -> "cash";
        };
    }

    private static String money(BigDecimal amount) {
        return amount == null ? "$0.00" : "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}

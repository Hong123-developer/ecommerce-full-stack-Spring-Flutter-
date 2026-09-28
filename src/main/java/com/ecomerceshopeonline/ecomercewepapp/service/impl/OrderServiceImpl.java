package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.config.AppProperties;
import com.ecomerceshopeonline.ecomercewepapp.dto.request.CheckoutRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.OrderResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.OrderSummaryResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.*;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentStatus;
import com.ecomerceshopeonline.ecomercewepapp.exception.BadRequestException;
import com.ecomerceshopeonline.ecomercewepapp.exception.ResourceNotFoundException;
import com.ecomerceshopeonline.ecomercewepapp.mapper.OrderMapper;
import com.ecomerceshopeonline.ecomercewepapp.repository.*;
import com.ecomerceshopeonline.ecomercewepapp.service.CartService;
import com.ecomerceshopeonline.ecomercewepapp.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final DateTimeFormatter DATE_PART = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int ORDER_NUMBER_ATTEMPTS = 5;

    /** Once an order reaches one of these it is finished - no further transitions. */
    private static final Set<OrderStatus> TERMINAL = EnumSet.of(
            OrderStatus.DELIVERED, OrderStatus.CANCELLED, OrderStatus.REFUNDED);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final AddressRepository addressRepository;
    private final CartService cartService;
    private final OrderMapper orderMapper;
    private final AppProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    // ---------------------------------------------------------------- checkout

    @Override
    @Transactional
    public OrderResponse checkout(Long userId, CheckoutRequest request) {
        Cart cart = cartService.requireCartEntity(userId);
        if (cart.getItems().isEmpty()) {
            throw new BadRequestException("Your cart is empty");
        }

        Address address = addressRepository.findByIdAndUserId(request.addressId(), userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Address", request.addressId()));

        User user = cart.getUser();
        Order order = Order.builder()
                .user(user)
                .orderNumber(generateOrderNumber())
                .status(OrderStatus.PENDING)
                .shippingAddress(address.toSingleLine())
                .deliveryFee(properties.order().deliveryFee())
                .discount(BigDecimal.ZERO)
                .subtotal(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();
            if (!product.isEnabled()) {
                throw new BadRequestException(
                        "'" + product.getName() + "' is no longer available. Remove it to continue.");
            }

            // Conditional UPDATE ... WHERE stock >= qty. A return of 0 means someone else
            // bought the last unit between rendering the cart and this line running, so we
            // fail the whole checkout rather than overselling. @Transactional rolls back
            // the decrements already applied to earlier items.
            int reserved = productRepository.decrementStock(product.getId(), cartItem.getQuantity());
            if (reserved == 0) {
                throw new BadRequestException("Not enough stock for '" + product.getName()
                        + "'. Only " + product.getStockQuantity() + " left.");
            }

            BigDecimal lineTotal = product.getPrice()
                    .multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(product)
                    // Snapshot: the order must survive a later rename or price change.
                    .productName(product.getName())
                    .quantity(cartItem.getQuantity())
                    .unitPrice(product.getPrice())
                    .subtotal(lineTotal)
                    .build();
            order.getItems().add(item);
            subtotal = subtotal.add(lineTotal);
        }

        order.setSubtotal(subtotal);
        order.setTotalAmount(order.calculateTotal());
        orderRepository.save(order);

        Payment payment = Payment.builder()
                .order(order)
                .user(user)
                .amount(order.getTotalAmount())
                .currency(properties.bakong().currency())
                .paymentMethod(request.paymentMethod())
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        paymentRepository.save(payment);
        order.setPayment(payment);

        cartService.clear(userId);
        log.info("Order {} created for user {} ({} items, total {})",
                order.getOrderNumber(), userId, order.getItems().size(), order.getTotalAmount());

        return orderMapper.toResponse(order, false);
    }

    // ---------------------------------------------------------------- customer

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listOwn(Long userId, Pageable pageable) {
        return PageResponse.from(orderRepository.findByUserWithPayment(userId, pageable),
                order -> orderMapper.toSummary(order, false));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOwn(Long userId, Long orderId) {
        return orderMapper.toResponse(requireOwned(userId, orderId), false);
    }

    @Override
    @Transactional
    public OrderResponse cancelOwn(Long userId, Long orderId) {
        Order order = requireOwned(userId, orderId);
        if (!OrderMapper.isCancellable(order.getStatus())) {
            throw new BadRequestException(
                    "An order that is already " + order.getStatus() + " cannot be cancelled");
        }
        applyCancellation(order);
        return orderMapper.toResponse(order, false);
    }

    // ---------------------------------------------------------------- admin

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> adminList(OrderStatus status, String search,
                                                        Pageable pageable) {
        return PageResponse.from(
                orderRepository.adminSearch(status, blankToNull(search), pageable),
                order -> orderMapper.toSummary(order, true));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse adminGet(Long orderId) {
        return orderMapper.toResponse(requireAny(orderId), true);
    }

    @Override
    @Transactional
    public OrderResponse adminUpdateStatus(Long orderId, OrderStatus status) {
        Order order = requireAny(orderId);

        if (order.getStatus() == status) {
            return orderMapper.toResponse(order, true);
        }
        if (TERMINAL.contains(order.getStatus())) {
            throw new BadRequestException(
                    "Order is already " + order.getStatus() + " and can no longer change");
        }

        if (status == OrderStatus.CANCELLED) {
            applyCancellation(order);
        } else {
            order.setStatus(status);
            // Marking an order delivered settles a cash-on-delivery payment.
            if (status == OrderStatus.DELIVERED && order.getPayment() != null
                    && order.getPayment().getPaymentStatus() == PaymentStatus.PENDING) {
                order.getPayment().setPaymentStatus(PaymentStatus.PAID);
                order.getPayment().setPaidAt(LocalDateTime.now());
                paymentRepository.save(order.getPayment());
            }
            orderRepository.save(order);
        }
        return orderMapper.toResponse(order, true);
    }

    // ---------------------------------------------------------------- helpers

    /** Cancels and returns every reserved unit to stock. */
    private void applyCancellation(Order order) {
        for (OrderItem item : order.getItems()) {
            if (item.getProduct() != null) {
                productRepository.incrementStock(item.getProduct().getId(), item.getQuantity());
            }
        }
        order.setStatus(OrderStatus.CANCELLED);

        Payment payment = order.getPayment();
        if (payment != null && payment.getPaymentStatus() == PaymentStatus.PENDING) {
            payment.setPaymentStatus(PaymentStatus.CANCELLED);
            paymentRepository.save(payment);
        }
        orderRepository.save(order);
        log.info("Order {} cancelled, stock restored", order.getOrderNumber());
    }

    private Order requireOwned(Long userId, Long orderId) {
        return orderRepository.findDetailByIdAndUserId(orderId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
    }

    private Order requireAny(Long orderId) {
        return orderRepository.findDetailById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
    }

    /** ORD-20260927-4F2A91 - date for readability, random suffix for unguessability. */
    private String generateOrderNumber() {
        String prefix = properties.order().numberPrefix();
        String date = LocalDateTime.now().format(DATE_PART);
        for (int attempt = 0; attempt < ORDER_NUMBER_ATTEMPTS; attempt++) {
            String candidate = "%s-%s-%06X".formatted(prefix, date, secureRandom.nextInt(0xFFFFFF));
            if (!orderRepository.existsByOrderNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Could not allocate a unique order number after " + ORDER_NUMBER_ATTEMPTS + " tries");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

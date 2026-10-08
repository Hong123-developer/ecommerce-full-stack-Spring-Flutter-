package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.config.AppProperties;
import com.ecomerceshopeonline.ecomercewepapp.dto.request.CheckoutRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.OrderResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.OrderSummaryResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.*;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.DeliveryType;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentMethod;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentStatus;
import com.ecomerceshopeonline.ecomercewepapp.exception.BadRequestException;
import com.ecomerceshopeonline.ecomercewepapp.exception.FieldValidationException;
import com.ecomerceshopeonline.ecomercewepapp.exception.ResourceNotFoundException;
import com.ecomerceshopeonline.ecomercewepapp.mapper.OrderMapper;
import com.ecomerceshopeonline.ecomercewepapp.repository.*;
import com.ecomerceshopeonline.ecomercewepapp.service.CardPaymentGateway;
import com.ecomerceshopeonline.ecomercewepapp.service.CartService;
import com.ecomerceshopeonline.ecomercewepapp.service.OrderService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final DateTimeFormatter DATE_PART = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int ORDER_NUMBER_ATTEMPTS = 5;

    /** Once an order reaches one of these it is finished - no further transitions. */
    private static final Set<OrderStatus> TERMINAL = EnumSet.of(
            OrderStatus.DELIVERED, OrderStatus.CANCELLED, OrderStatus.REFUNDED);

    /** Shipping address text of a pickup order (the store address follows when there is one). */
    static final String PICKUP_ADDRESS = "Pick up at store";

    /** Method name on an order placed while no delivery option was configured at all. */
    static final String LEGACY_DELIVERY_NAME = "Standard delivery";

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final AddressRepository addressRepository;
    private final DeliveryOptionRepository deliveryOptionRepository;
    private final StoreSettingsRepository storeSettingsRepository;
    private final CartService cartService;
    private final OrderMapper orderMapper;
    private final AppProperties properties;
    private final CardPaymentGateway cardPaymentGateway;
    private final SecureRandom secureRandom = new SecureRandom();

    @PersistenceContext
    private EntityManager entityManager;

    /** Global lock order for stock rows: product id, then variant id (plain line first). */
    private static final Comparator<CartItem> LINE_ORDER = Comparator
            .comparing((CartItem line) -> line.getProduct().getId())
            .thenComparing(line -> line.getVariant() == null ? null : line.getVariant().getId(),
                    Comparator.nullsFirst(Comparator.<Long>naturalOrder()));

    private static final Comparator<OrderItem> ITEM_ORDER = Comparator
            .comparing((OrderItem item) -> item.getProduct() == null ? null : item.getProduct().getId(),
                    Comparator.nullsLast(Comparator.<Long>naturalOrder()))
            .thenComparing(item -> item.getVariant() == null ? null : item.getVariant().getId(),
                    Comparator.nullsFirst(Comparator.<Long>naturalOrder()));

    // ---------------------------------------------------------------- checkout

    @Override
    @Transactional
    public OrderResponse checkout(Long userId, CheckoutRequest request) {
        if (request.paymentMethod() == PaymentMethod.CARD && !cardPaymentGateway.isConfigured()) {
            throw new FieldValidationException("paymentMethod", "Card payment is not available yet");
        }
        Cart cart = cartService.requireCartEntity(userId);
        if (cart.getItems().isEmpty()) {
            throw new BadRequestException("Your cart is empty");
        }

        DeliveryChoice delivery = resolveDelivery(request.deliveryOptionId());
        Destination destination = delivery.type() == DeliveryType.PICKUP
                ? pickupDestination(request)
                : deliveryDestination(userId, request);

        User user = cart.getUser();
        Order order = Order.builder()
                .user(user)
                .orderNumber(generateOrderNumber())
                .status(OrderStatus.PENDING)
                // Snapshots: the order must survive a later edit of the option or the address.
                .deliveryOption(delivery.option())
                .deliveryMethodName(delivery.methodName())
                .deliveryType(delivery.type())
                .deliveryFee(delivery.fee())
                .shippingAddress(destination.shippingAddress())
                .latitude(destination.latitude())
                .longitude(destination.longitude())
                .contactName(destination.contactName())
                .contactPhone(destination.contactPhone())
                .customerNote(request.note())
                .discount(BigDecimal.ZERO)
                .subtotal(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();
            ProductVariant variant = cartItem.getVariant();
            String displayName = displayName(product, variant);
            if (!product.isEnabled() || (variant != null && !variant.isEnabled())) {
                throw new BadRequestException(
                        "'" + displayName + "' is no longer available. Remove it to continue.");
            }
            if (variant == null && product.hasEnabledVariants()) {
                throw new BadRequestException(
                        "Please choose an option for '" + product.getName() + "'. Remove it to continue.");
            }

            BigDecimal unitPrice = variant == null ? product.getPrice() : variant.getPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .variant(variant)
                    // Snapshot: the order must survive a later rename or price change.
                    .productName(product.getName())
                    .variantLabel(variant == null ? null : variant.getLabel())
                    .sku(variant == null ? product.getSku() : variant.getSku())
                    .quantity(cartItem.getQuantity())
                    .unitPrice(unitPrice)
                    .subtotal(lineTotal)
                    .build();
            order.getItems().add(item);
            subtotal = subtotal.add(lineTotal);
        }

        reserveStock(cart.getItems());

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
        log.info("Order {} created for user {} ({} items, {} via '{}', total {})",
                order.getOrderNumber(), userId, order.getItems().size(), order.getDeliveryType(),
                order.getDeliveryMethodName(), order.getTotalAmount());

        return orderMapper.toResponse(order, false);
    }

    /** The answer to "how do you want to receive it?", resolved to what the order records. */
    private record DeliveryChoice(DeliveryOption option, DeliveryType type, String methodName,
                                  BigDecimal fee) {

        static DeliveryChoice of(DeliveryOption option) {
            return new DeliveryChoice(option, option.getType(), option.getName(), option.getFee());
        }
    }

    /** Where the order goes and who to call about it. */
    private record Destination(String shippingAddress, String contactName, String contactPhone,
                               Double latitude, Double longitude) {
    }

    /**
     * The named option when it exists and is enabled. A request that names none comes from a
     * client older than delivery options, which always meant home delivery: it gets the first
     * enabled DELIVERY option. Only while no option is configured at all (a fresh database
     * before the seeder has run) does the old flat {@code app.order.delivery-fee} apply.
     */
    private DeliveryChoice resolveDelivery(Long optionId) {
        if (optionId != null) {
            return deliveryOptionRepository.findById(optionId)
                    .filter(DeliveryOption::isEnabled)
                    .map(DeliveryChoice::of)
                    .orElseThrow(() -> new FieldValidationException("deliveryOptionId",
                            "This delivery option is not available. Please choose another one."));
        }
        Optional<DeliveryOption> firstDelivery = deliveryOptionRepository
                .findFirstByTypeAndEnabledTrueOrderBySortOrderAscIdAsc(DeliveryType.DELIVERY);
        if (firstDelivery.isPresent()) {
            return DeliveryChoice.of(firstDelivery.get());
        }
        if (deliveryOptionRepository.existsByEnabledTrue()) {
            // Options are set up, just none that delivers (e.g. pickup only) - the shopper
            // has to choose one rather than silently get a delivery the shop does not offer.
            throw new FieldValidationException("deliveryOptionId",
                    "Choose how you want to receive your order");
        }
        return new DeliveryChoice(null, DeliveryType.DELIVERY, LEGACY_DELIVERY_NAME,
                Objects.requireNonNullElse(properties.order().deliveryFee(), BigDecimal.ZERO));
    }

    /** Home delivery needs one of the shopper's own addresses; it also supplies the contact defaults. */
    private Destination deliveryDestination(Long userId, CheckoutRequest request) {
        if (request.addressId() == null) {
            throw new FieldValidationException("addressId", "Choose a delivery address");
        }
        Address address = addressRepository.findByIdAndUserId(request.addressId(), userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Address", request.addressId()));
        return new Destination(
                address.toSingleLine(),
                request.contactName() != null ? request.contactName() : address.getRecipientName(),
                request.contactPhone() != null ? request.contactPhone() : address.getPhone(),
                address.getLatitude(),
                address.getLongitude());
    }

    /** Pickup needs no address (any addressId is ignored), but the store must know who is coming. */
    private Destination pickupDestination(CheckoutRequest request) {
        Map<String, String> missing = new LinkedHashMap<>();
        if (request.contactName() == null) {
            missing.put("contactName", "Customer name is required for store pickup");
        }
        if (request.contactPhone() == null) {
            missing.put("contactPhone", "Phone number is required for store pickup");
        }
        if (!missing.isEmpty()) {
            throw new FieldValidationException(missing);
        }
        String storeAddress = storeSettingsRepository.findSingleton()
                .map(StoreSettings::getAddress)
                .filter(text -> !text.isBlank())
                .map(String::trim)
                .orElse(null);
        String shippingAddress = storeAddress == null
                ? PICKUP_ADDRESS
                : PICKUP_ADDRESS + " - " + storeAddress;
        return new Destination(shippingAddress, request.contactName(), request.contactPhone(),
                null, null);
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
        // A card payment may be settling right now (callback / poll); see its latest state.
        lockPayment(order);
        if (!OrderMapper.isCancellable(order.getStatus())) {
            throw new BadRequestException(
                    "An order that is already " + order.getStatus() + " cannot be cancelled");
        }
        if (!OrderMapper.isSelfCancellable(order)) {
            throw new BadRequestException(
                    "This order has already been paid. Please contact us to cancel it and arrange a refund.");
        }
        applyCancellation(order, PaymentStatus.CANCELLED);
        return orderMapper.toResponse(order, false);
    }

    @Override
    @Transactional
    public boolean expireUnpaidOrder(Long orderId) {
        Order order = requireAny(orderId);
        lockPayment(order);
        Payment payment = order.getPayment();
        boolean unpaid = payment != null && (payment.getPaymentStatus() == PaymentStatus.PENDING
                || payment.getPaymentStatus() == PaymentStatus.FAILED);
        // Only an order nobody has started working on: once an admin moved it on, it is theirs.
        if (!unpaid || order.getStatus() != OrderStatus.PENDING) {
            return false;
        }
        applyCancellation(order, PaymentStatus.EXPIRED);
        log.info("Order {} expired unpaid ({}), stock restored",
                order.getOrderNumber(), payment.getPaymentMethod());
        return true;
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

        lockPayment(order);
        if (status == OrderStatus.CANCELLED) {
            applyCancellation(order, PaymentStatus.CANCELLED);
        } else {
            order.setStatus(status);
            // Marking an order delivered settles a cash-on-delivery payment. A card payment is
            // only ever settled by the card provider's confirmation (or an explicit verify).
            if (status == OrderStatus.DELIVERED && order.getPayment() != null
                    && order.getPayment().getPaymentMethod() != PaymentMethod.CARD
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

    /**
     * Conditional UPDATE ... WHERE stock >= qty for every line. A return of 0 means someone
     * else bought the last unit between rendering the cart and this running, so the whole
     * checkout fails rather than overselling; @Transactional rolls back what was applied.
     * <p>
     * Row locks are always taken in the same global order to rule out deadlocks between
     * concurrent checkouts / cancellations: first every variant row (sorted by product id,
     * then variant id), then every product row once, in product id order. Product totals of
     * variant products move by the same relative amount as their variants - never an absolute
     * SUM recompute, which can lose a concurrent order's change under READ COMMITTED.
     */
    private void reserveStock(List<CartItem> cartItems) {
        List<CartItem> lines = cartItems.stream().sorted(LINE_ORDER).toList();

        Map<Long, Integer> variantProductDelta = new TreeMap<>();
        for (CartItem line : lines) {
            ProductVariant variant = line.getVariant();
            if (variant == null) {
                continue;
            }
            if (variantRepository.decrementStock(variant.getId(), line.getQuantity()) == 0) {
                throw notEnoughStock(line.getProduct(), variant, variant.getStockQuantity());
            }
            variantProductDelta.merge(line.getProduct().getId(), line.getQuantity(), Integer::sum);
        }

        // Product rows, each touched once, ascending id. A product is bought either as plain
        // lines or as variant lines (checkout rejects a plain line on a variant product).
        Map<Long, Integer> plainQuantity = new TreeMap<>();
        Map<Long, CartItem> plainLine = new HashMap<>();
        for (CartItem line : lines) {
            if (line.getVariant() == null) {
                plainQuantity.merge(line.getProduct().getId(), line.getQuantity(), Integer::sum);
                plainLine.putIfAbsent(line.getProduct().getId(), line);
            }
        }
        Set<Long> productIds = new TreeSet<>(variantProductDelta.keySet());
        productIds.addAll(plainQuantity.keySet());
        for (Long productId : productIds) {
            Integer plain = plainQuantity.get(productId);
            if (plain != null && productRepository.decrementStock(productId, plain) == 0) {
                Product product = plainLine.get(productId).getProduct();
                throw notEnoughStock(product, null, product.getStockQuantity());
            }
            Integer fromVariants = variantProductDelta.get(productId);
            if (fromVariants != null) {
                // Every variant decremented above was enabled, so the product total (the sum
                // of its enabled variants) drops by exactly the same amount.
                productRepository.incrementStock(productId, -fromVariants);
            }
        }
    }

    private static BadRequestException notEnoughStock(Product product, ProductVariant variant, int left) {
        return new BadRequestException("Not enough stock for '" + displayName(product, variant)
                + "'. Only " + left + " left.");
    }

    private static String displayName(Product product, ProductVariant variant) {
        return variant == null ? product.getName() : product.getName() + " (" + variant.getLabel() + ")";
    }

    /**
     * Cancels and returns every reserved unit to stock (same lock order as checkout).
     * An unpaid payment ends as {@code unpaidOutcome} (CANCELLED, or EXPIRED for an order
     * whose card payment window ran out).
     */
    private void applyCancellation(Order order, PaymentStatus unpaidOutcome) {
        List<OrderItem> items = order.getItems().stream()
                .filter(item -> item.getProduct() != null || item.getVariant() != null)
                .sorted(ITEM_ORDER)
                .toList();
        for (OrderItem item : items) {
            if (item.getVariant() != null) {
                variantRepository.incrementStock(item.getVariant().getId(), item.getQuantity());
            }
        }
        for (OrderItem item : items) {
            if (item.getProduct() == null) {
                continue;
            }
            if (item.getVariant() == null) {
                // Ordered while plain; if the product has enabled variants now, its stock is
                // their sum and the returned units have nowhere to go.
                productRepository.incrementStockIfPlain(item.getProduct().getId(), item.getQuantity());
            } else {
                // Counts towards the product total only while that variant is still enabled.
                productRepository.adjustStockForVariant(
                        item.getProduct().getId(), item.getVariant().getId(), item.getQuantity());
            }
        }
        order.setStatus(OrderStatus.CANCELLED);

        Payment payment = order.getPayment();
        // FAILED is a card attempt that was declined - still unpaid, so it ends here too.
        if (payment != null && (payment.getPaymentStatus() == PaymentStatus.PENDING
                || payment.getPaymentStatus() == PaymentStatus.FAILED)) {
            payment.setPaymentStatus(unpaidOutcome);
            paymentRepository.save(payment);
        } else if (payment != null && payment.getPaymentStatus() == PaymentStatus.PAID) {
            // Allowed (an admin's call), but the money stays taken until refunded by hand.
            log.warn("Order {} cancelled although its {} payment {} is PAID - refund it manually{}",
                    order.getOrderNumber(), payment.getPaymentMethod(), payment.getId(),
                    payment.getGatewayProvider() == null ? ""
                            : " in the " + payment.getGatewayProvider() + " merchant portal");
        }
        orderRepository.save(order);
        log.info("Order {} cancelled, stock restored", order.getOrderNumber());
    }

    /**
     * Re-reads the order's payment under a row lock. Card payments settle from outside (the
     * provider's callback, the shopper's poll); without this a cancel that read the payment
     * a moment earlier could overwrite PAID with CANCELLED.
     */
    private void lockPayment(Order order) {
        Payment payment = order.getPayment();
        if (payment != null && payment.getId() != null) {
            entityManager.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        }
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

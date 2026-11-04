package com.ecomerceshopeonline.ecomercewepapp.entity;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.DeliveryType;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.ShipmentStage;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "orders",
        uniqueConstraints = @UniqueConstraint(name = "uk_orders_order_number", columnNames = "order_number"),
        indexes = {
                @Index(name = "idx_orders_user", columnList = "user_id"),
                @Index(name = "idx_orders_status", columnList = "status"),
                @Index(name = "idx_orders_delivery_option", columnList = "delivery_option_id")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-facing reference, e.g. ORD-20260927-0001. */
    @Column(name = "order_number", nullable = false, length = 40)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_orders_user")
    )
    private User user;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "delivery_fee", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(name = "discount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "coupon_code", length = 40)
    private String couponCode;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    /** Address snapshot taken at checkout, so later edits to the address book do not rewrite history. */
    @Column(name = "shipping_address", nullable = false, length = 500)
    private String shippingAddress;

    // ---- how the shopper receives it. Every column below is null on orders placed before
    // delivery options existed, so readers must cope with that.

    /** The option chosen at checkout; it can be disabled later but never deleted while used. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "delivery_option_id",
            foreignKey = @ForeignKey(name = "fk_orders_delivery_option")
    )
    private DeliveryOption deliveryOption;

    /** Snapshot of the option's name, so renaming the option does not rewrite history. */
    @Column(name = "delivery_method_name", length = 80)
    private String deliveryMethodName;

    /** Snapshot of the option's type. */
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", length = 20)
    private DeliveryType deliveryType;

    /** Who to call about the order: the address's recipient unless the shopper gave someone else. */
    @Column(name = "contact_name", length = 150)
    private String contactName;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    /** Free-text note from the shopper, e.g. "call before arriving". */
    @Column(name = "customer_note", length = 500)
    private String customerNote;

    /** Map pin of the delivery address at checkout; null for pickup or an address without one. */
    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    // ---- the courier's side of a shipped order, filled in by an admin. Every column below
    // is null until then (and always on older orders), so readers must cope with that.

    /** The courier's parcel reference, e.g. JT8800123456. */
    @Column(name = "tracking_number", length = 60)
    private String trackingNumber;

    /** Who carries it, e.g. "J&T Express" - may differ from the option the shopper chose. */
    @Column(name = "carrier_name", length = 80)
    private String carrierName;

    /** The rider bringing it, for the shopper to recognise and call. */
    @Column(name = "courier_name", length = 120)
    private String courierName;

    @Column(name = "courier_phone", length = 30)
    private String courierPhone;

    /** When the store expects it to arrive. */
    @Column(name = "estimated_delivery_at")
    private LocalDateTime estimatedDeliveryAt;

    /** The courier's step while SHIPPED; cleared whenever the order moves to another status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "shipment_stage", length = 20)
    private ShipmentStage shipmentStage;

    /** The courier's last reported position, for the live map. */
    @Column(name = "courier_latitude")
    private Double courierLatitude;

    @Column(name = "courier_longitude")
    private Double courierLongitude;

    /** When that position was reported - lets the app tell a live pin from a stale one. */
    @Column(name = "courier_location_at")
    private LocalDateTime courierLocationAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL)
    private Payment payment;

    public void addItem(OrderItem item) {
        this.items.add(item);
        item.setOrder(this);
    }

    /** subtotal + delivery - discount. Call before persisting. */
    public BigDecimal calculateTotal() {
        BigDecimal base = subtotal == null ? BigDecimal.ZERO : subtotal;
        BigDecimal fee = deliveryFee == null ? BigDecimal.ZERO : deliveryFee;
        BigDecimal off = discount == null ? BigDecimal.ZERO : discount;
        return base.add(fee).subtract(off);
    }
}

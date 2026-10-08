package com.ecomerceshopeonline.ecomercewepapp.entity;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.DeliveryType;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
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

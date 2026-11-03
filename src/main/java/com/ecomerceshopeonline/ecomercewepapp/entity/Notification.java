package com.ecomerceshopeonline.ecomercewepapp.entity;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.DeliveryType;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.NotificationType;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notifications_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_notifications_user_unread", columnList = "user_id, read_at")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Who sees it. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_notifications_user")
    )
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    /** In-app path to open, e.g. /admin/orders/12 or /orders/12. */
    @Column(name = "link", length = 255)
    private String link;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "order_number", length = 40)
    private String orderNumber;

    /** The order's status this notification announces (null when that is not the point). */
    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", length = 20)
    private OrderStatus orderStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 30)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", length = 20)
    private DeliveryType deliveryType;

    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;

    /** The customer's name, on admin notifications. */
    @Column(name = "actor_name", length = 120)
    private String actorName;

    /** Null while unread. */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

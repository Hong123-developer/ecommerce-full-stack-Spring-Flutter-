package com.ecomerceshopeonline.ecomercewepapp.entity;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentMethod;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payments_transaction", columnNames = "transaction_id"),
                @UniqueConstraint(name = "uk_payments_gateway_tran", columnNames = "gateway_tran_id")
        },
        indexes = {
                @Index(name = "idx_payments_order", columnList = "order_id"),
                @Index(name = "idx_payments_user", columnList = "user_id"),
                @Index(name = "idx_payments_status", columnList = "payment_status")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "order_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_payments_order")
    )
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_payments_user")
    )
    private User user;

    /** Reference returned by the payment provider (e.g. the Bakong transaction hash). */
    @Column(name = "transaction_id", length = 120)
    private String transactionId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    /** Raw KHQR payload string handed to the customer's banking app. */
    @Column(name = "khqr", columnDefinition = "TEXT")
    private String khqr;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // ---- card payments through a hosted payment page (e.g. ABA PayWay). All null for other
    // methods and for payments made before card existed. Card numbers, CVV and expiry never
    // reach this server - the provider's page collects them.

    /** Display name of the card provider, e.g. "ABA PayWay". */
    @Column(name = "gateway_provider", length = 20)
    private String gatewayProvider;

    /** Our id of the latest attempt at the provider; every new attempt gets a fresh one. */
    @Column(name = "gateway_tran_id", length = 40)
    private String gatewayTranId;

    /** Approval code ("apv") of the authorisation, once paid. */
    @Column(name = "gateway_approval_code", length = 40)
    private String gatewayApprovalCode;

    /** Brand and last four digits only, e.g. "VISA •••• 4242" - never the full number. */
    @Column(name = "card_masked", length = 40)
    private String cardMasked;

    public boolean isSettled() {
        return paymentStatus == PaymentStatus.PAID;
    }
}

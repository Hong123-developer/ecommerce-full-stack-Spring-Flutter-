package com.ecomerceshopeonline.ecomercewepapp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;


@Entity
@Table(
        name = "payment_account",
        check = @CheckConstraint(name = "ck_payment_account_singleton", constraint = "id = 1")
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class PaymentAccount extends BaseEntity {

    public static final long SINGLETON_ID = 1L;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** e.g. "ACLEDA Bank". */
    @Column(name = "bank_name", length = 60)
    private String bankName;

    /** The name on the account, e.g. "Prol Menghong". */
    @Column(name = "account_name", length = 100)
    private String accountName;

    /** The account that receives US dollars. */
    @Column(name = "usd_account_number", length = 40)
    private String usdAccountNumber;

    /** The account that receives riel. */
    @Column(name = "khr_account_number", length = 40)
    private String khrAccountNumber;
}

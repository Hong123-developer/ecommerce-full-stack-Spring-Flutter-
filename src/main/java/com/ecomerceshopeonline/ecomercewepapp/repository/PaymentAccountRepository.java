package com.ecomerceshopeonline.ecomercewepapp.repository;

import com.ecomerceshopeonline.ecomercewepapp.entity.PaymentAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentAccountRepository extends JpaRepository<PaymentAccount, Long> {

    /** The one row, or empty when the admin has never saved any. */
    default Optional<PaymentAccount> findSingleton() {
        return findById(PaymentAccount.SINGLETON_ID);
    }
}

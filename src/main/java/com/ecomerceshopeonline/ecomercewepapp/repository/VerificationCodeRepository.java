package com.ecomerceshopeonline.ecomercewepapp.repository;

import com.ecomerceshopeonline.ecomercewepapp.entity.VerificationCode;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.VerificationPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {

    Optional<VerificationCode> findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(
            Long userId, VerificationPurpose purpose);

    @Modifying
    @Query("DELETE FROM VerificationCode v WHERE v.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") LocalDateTime cutoff);

    /** Account deletion - removes every code issued to the user. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM VerificationCode v WHERE v.user.id = :userId")
    int bulkDeleteByUserId(@Param("userId") Long userId);
}

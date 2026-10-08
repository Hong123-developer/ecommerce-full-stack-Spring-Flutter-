package com.ecomerceshopeonline.ecomercewepapp.config;

import com.ecomerceshopeonline.ecomercewepapp.entity.DeliveryOption;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.DeliveryType;
import com.ecomerceshopeonline.ecomercewepapp.repository.DeliveryOptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Gives a fresh shop the usual Cambodian delivery choices plus store pickup, so the checkout
 * has something to offer before the admin has configured anything. Only runs while the
 * table is empty: once the admin has edited, disabled or deleted options, their choices
 * stand. Idempotent - safe to run on every startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryOptionSeeder implements ApplicationRunner {

    private final DeliveryOptionRepository deliveryOptionRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedDefaults();
    }

    /** Inserts the defaults when there are no options at all; otherwise does nothing. */
    @Transactional
    public void seedDefaults() {
        if (deliveryOptionRepository.count() > 0) {
            return;
        }
        List<DeliveryOption> defaults = List.of(
                option("J&T Express", "Door-to-door delivery nationwide",
                        DeliveryType.DELIVERY, "1.50", "1–2 days", 0),
                option("Virak Buntham Express (VET)", "Parcel delivery to every province",
                        DeliveryType.DELIVERY, "2.00", "1–3 days", 1),
                option("Store rider (Phnom Penh)", "Our own rider, within Phnom Penh only",
                        DeliveryType.DELIVERY, "1.50", "Same day", 2),
                option("Pick up at store", "Collect your order from our shop - no delivery fee",
                        DeliveryType.PICKUP, "0.00", "Ready in about 2 hours", 3));
        deliveryOptionRepository.saveAll(defaults);
        log.info("Seeded {} default delivery options", defaults.size());
    }

    private static DeliveryOption option(String name, String description, DeliveryType type,
                                         String fee, String estimatedTime, int sortOrder) {
        return DeliveryOption.builder()
                .name(name)
                .description(description)
                .type(type)
                .fee(new BigDecimal(fee))
                .estimatedTime(estimatedTime)
                .sortOrder(sortOrder)
                .enabled(true)
                .build();
    }
}

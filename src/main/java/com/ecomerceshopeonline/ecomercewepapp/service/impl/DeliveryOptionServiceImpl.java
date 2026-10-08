package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.DeliveryOptionRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.DeliveryOptionResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.DeliveryOption;
import com.ecomerceshopeonline.ecomercewepapp.exception.BadRequestException;
import com.ecomerceshopeonline.ecomercewepapp.exception.ConflictException;
import com.ecomerceshopeonline.ecomercewepapp.exception.ResourceNotFoundException;
import com.ecomerceshopeonline.ecomercewepapp.mapper.DeliveryOptionMapper;
import com.ecomerceshopeonline.ecomercewepapp.repository.DeliveryOptionRepository;
import com.ecomerceshopeonline.ecomercewepapp.service.DeliveryOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeliveryOptionServiceImpl implements DeliveryOptionService {

    static final String LAST_ENABLED_MESSAGE =
            "At least one delivery option must stay enabled, or customers cannot check out";

    private final DeliveryOptionRepository deliveryOptionRepository;
    private final DeliveryOptionMapper deliveryOptionMapper;

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryOptionResponse> browse() {
        return deliveryOptionRepository.findByEnabledTrueOrderBySortOrderAscIdAsc().stream()
                .map(deliveryOptionMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryOptionResponse> listAll() {
        return deliveryOptionRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(deliveryOptionMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryOptionResponse getById(Long id) {
        return deliveryOptionMapper.toResponse(require(id));
    }

    @Override
    @Transactional
    public DeliveryOptionResponse create(DeliveryOptionRequest request) {
        // No explicit position means "after the last option", not "0" - otherwise every new
        // option would jump to the top of the checkout list.
        int sortOrder = request.sortOrder() != null
                ? request.sortOrder()
                : deliveryOptionRepository.findMaxSortOrder() + 1;
        return deliveryOptionMapper.toResponse(
                deliveryOptionRepository.save(deliveryOptionMapper.toEntity(request, sortOrder)));
    }

    @Override
    @Transactional
    public DeliveryOptionResponse update(Long id, DeliveryOptionRequest request) {
        if (Boolean.FALSE.equals(request.enabled())) {
            guardLastEnabled(id);
        }
        DeliveryOption option = require(id);
        deliveryOptionMapper.apply(request, option);
        // Flushed now so the auditing listener has stamped updatedAt before the response is built.
        return deliveryOptionMapper.toResponse(deliveryOptionRepository.saveAndFlush(option));
    }

    @Override
    @Transactional
    public DeliveryOptionResponse setEnabled(Long id, boolean enabled) {
        if (!enabled) {
            guardLastEnabled(id);
        }
        DeliveryOption option = require(id);
        option.setEnabled(enabled);
        return deliveryOptionMapper.toResponse(deliveryOptionRepository.saveAndFlush(option));
    }

    @Override
    @Transactional
    public List<DeliveryOptionResponse> reorder(List<Long> ids) {
        Map<Long, DeliveryOption> byId = new HashMap<>();
        for (DeliveryOption option : deliveryOptionRepository.findAll()) {
            byId.put(option.getId(), option);
        }
        // Same size AND same set rules out missing, unknown and duplicated ids alike. A
        // partial list is refused rather than merged: the options it leaves out would keep
        // stale positions that collide with the new ones.
        if (ids.size() != byId.size() || !new HashSet<>(ids).equals(byId.keySet())) {
            throw new BadRequestException("Order must list every delivery option exactly once");
        }
        for (int index = 0; index < ids.size(); index++) {
            byId.get(ids.get(index)).setSortOrder(index);
        }
        deliveryOptionRepository.saveAll(byId.values());
        return listAll();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        // Checked before "is it used": the advice "disable it instead" would be wrong here.
        guardLastEnabled(id);
        DeliveryOption option = require(id);
        long orders = deliveryOptionRepository.countOrdersUsing(id);
        if (orders > 0) {
            throw new ConflictException(
                    "This option is used by " + orders + " order(s). Disable it instead.");
        }
        deliveryOptionRepository.delete(option);
    }

    /**
     * Refuses to switch off or remove the only enabled option - the checkout would then have
     * nothing to offer. Runs before the option itself is loaded, so its enabled flag is read
     * under the lock and not from a copy loaded earlier in the transaction.
     */
    private void guardLastEnabled(Long id) {
        List<DeliveryOption> enabled = deliveryOptionRepository.lockAllEnabled();
        boolean targetIsEnabled = enabled.stream().anyMatch(option -> option.getId().equals(id));
        if (targetIsEnabled && enabled.size() == 1) {
            throw new BadRequestException(LAST_ENABLED_MESSAGE);
        }
    }

    private DeliveryOption require(Long id) {
        return deliveryOptionRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Delivery option", id));
    }
}

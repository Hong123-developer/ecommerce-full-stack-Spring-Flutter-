package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.HeroSlideRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.HeroSlideResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.HeroSlide;
import com.ecomerceshopeonline.ecomercewepapp.exception.BadRequestException;
import com.ecomerceshopeonline.ecomercewepapp.exception.ResourceNotFoundException;
import com.ecomerceshopeonline.ecomercewepapp.mapper.HeroSlideMapper;
import com.ecomerceshopeonline.ecomercewepapp.repository.HeroSlideRepository;
import com.ecomerceshopeonline.ecomercewepapp.service.HeroSlideService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HeroSlideServiceImpl implements HeroSlideService {

    private final HeroSlideRepository heroSlideRepository;
    private final HeroSlideMapper heroSlideMapper;

    @Override
    @Transactional(readOnly = true)
    public List<HeroSlideResponse> browse() {
        return heroSlideRepository.findByEnabledTrueOrderBySortOrderAscIdAsc().stream()
                .map(heroSlideMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HeroSlideResponse> listAll() {
        return heroSlideRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(heroSlideMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public HeroSlideResponse getById(Long id) {
        return heroSlideMapper.toResponse(require(id));
    }

    @Override
    @Transactional
    public HeroSlideResponse create(HeroSlideRequest request) {
        // No explicit position means "put it after the last slide", not "0" - otherwise
        // every new slide would jump to the front of the carousel.
        int sortOrder = request.sortOrder() != null
                ? request.sortOrder()
                : heroSlideRepository.findMaxSortOrder() + 1;
        return heroSlideMapper.toResponse(
                heroSlideRepository.save(heroSlideMapper.toEntity(request, sortOrder)));
    }

    @Override
    @Transactional
    public HeroSlideResponse update(Long id, HeroSlideRequest request) {
        HeroSlide slide = require(id);
        heroSlideMapper.apply(request, slide);
        return heroSlideMapper.toResponse(heroSlideRepository.save(slide));
    }

    @Override
    @Transactional
    public HeroSlideResponse setEnabled(Long id, boolean enabled) {
        HeroSlide slide = require(id);
        slide.setEnabled(enabled);
        return heroSlideMapper.toResponse(heroSlideRepository.save(slide));
    }

    @Override
    @Transactional
    public List<HeroSlideResponse> reorder(List<Long> ids) {
        Map<Long, HeroSlide> byId = new HashMap<>();
        for (HeroSlide slide : heroSlideRepository.findAll()) {
            byId.put(slide.getId(), slide);
        }
        // Same size AND same set rules out missing, unknown and duplicated ids alike.
        // A partial list is refused rather than merged: the slides it leaves out would
        // keep stale positions that collide with the new ones.
        if (ids.size() != byId.size() || !new HashSet<>(ids).equals(byId.keySet())) {
            throw new BadRequestException("Order must list every slide exactly once");
        }
        for (int index = 0; index < ids.size(); index++) {
            byId.get(ids.get(index)).setSortOrder(index);
        }
        heroSlideRepository.saveAll(byId.values());
        return listAll();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        heroSlideRepository.delete(require(id));
    }

    private HeroSlide require(Long id) {
        return heroSlideRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Hero slide", id));
    }
}

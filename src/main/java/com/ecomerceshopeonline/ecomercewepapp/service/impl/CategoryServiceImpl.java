package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.CategoryRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.CategoryResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.Category;
import com.ecomerceshopeonline.ecomercewepapp.exception.ConflictException;
import com.ecomerceshopeonline.ecomercewepapp.exception.ResourceNotFoundException;
import com.ecomerceshopeonline.ecomercewepapp.mapper.CategoryMapper;
import com.ecomerceshopeonline.ecomercewepapp.repository.CategoryRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.ProductRepository;
import com.ecomerceshopeonline.ecomercewepapp.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> browse(Pageable pageable) {
        return PageResponse.from(
                categoryRepository.findByEnabledTrue(pageable), categoryMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getPublic(Long id) {
        Category category = require(id);
        if (!category.isEnabled()) {
            // Hidden categories must look absent to shoppers, not "forbidden".
            throw ResourceNotFoundException.of("Category", id);
        }
        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> listAll(Pageable pageable) {
        return PageResponse.from(categoryRepository.findAll(pageable), categoryMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        return categoryMapper.toResponse(require(id));
    }

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByName(name)) {
            throw new ConflictException("A category named '" + name + "' already exists");
        }
        return categoryMapper.toResponse(
                categoryRepository.save(categoryMapper.toEntity(request)));
    }

    @Override
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = require(id);
        String name = request.name().trim();
        // Allow saving without changing the name; block only a clash with a different row.
        if (!category.getName().equalsIgnoreCase(name) && categoryRepository.existsByName(name)) {
            throw new ConflictException("A category named '" + name + "' already exists");
        }
        categoryMapper.apply(request, category);
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public CategoryResponse setEnabled(Long id, boolean enabled) {
        Category category = require(id);
        category.setEnabled(enabled);
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Category category = require(id);
        if (productRepository.existsByCategoryId(id)) {
            // Deleting would either orphan products or blow up on the FK - say so clearly.
            throw new ConflictException(
                    "This category still has products. Move or delete them first, "
                            + "or disable the category instead.");
        }
        categoryRepository.delete(category);
    }

    private Category require(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Category", id));
    }
}

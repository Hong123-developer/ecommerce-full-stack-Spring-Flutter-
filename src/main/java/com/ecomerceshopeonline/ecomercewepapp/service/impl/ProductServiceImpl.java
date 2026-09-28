package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.ProductRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ProductResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.Category;
import com.ecomerceshopeonline.ecomercewepapp.entity.Product;
import com.ecomerceshopeonline.ecomercewepapp.exception.BadRequestException;
import com.ecomerceshopeonline.ecomercewepapp.exception.ConflictException;
import com.ecomerceshopeonline.ecomercewepapp.exception.ResourceNotFoundException;
import com.ecomerceshopeonline.ecomercewepapp.mapper.ProductMapper;
import com.ecomerceshopeonline.ecomercewepapp.repository.CartItemRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.CategoryRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.OrderItemRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.ProductRepository;
import com.ecomerceshopeonline.ecomercewepapp.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductMapper productMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> browse(String search, Long categoryId,
                                                BigDecimal minPrice, BigDecimal maxPrice,
                                                Pageable pageable) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("minPrice cannot be greater than maxPrice");
        }
        return PageResponse.from(
                productRepository.browse(blankToNull(search), categoryId, minPrice, maxPrice, pageable),
                productMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getPublic(Long id) {
        Product product = requireWithCategory(id);
        if (!product.isEnabled()) {
            // Hidden products must look absent to shoppers, not "forbidden".
            throw ResourceNotFoundException.of("Product", id);
        }
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> listAll(Pageable pageable) {
        return PageResponse.from(
                productRepository.findAllWithCategory(pageable), productMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return productMapper.toResponse(requireWithCategory(id));
    }

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        String sku = request.sku().trim();
        if (productRepository.existsBySku(sku)) {
            throw new ConflictException("A product with SKU '" + sku + "' already exists");
        }
        Category category = requireCategory(request.categoryId());
        return productMapper.toResponse(
                productRepository.save(productMapper.toEntity(request, category)));
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = requireWithCategory(id);
        String sku = request.sku().trim();
        // Allow saving without changing the SKU; block only a clash with a different row.
        if (!product.getSku().equalsIgnoreCase(sku) && productRepository.existsBySku(sku)) {
            throw new ConflictException("A product with SKU '" + sku + "' already exists");
        }
        productMapper.apply(request, requireCategory(request.categoryId()), product);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse setEnabled(Long id, boolean enabled) {
        Product product = requireWithCategory(id);
        product.setEnabled(enabled);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Product product = requireWithCategory(id);

        if (orderItemRepository.existsByProductId(id)) {
            throw new ConflictException(
                    "This product appears in past orders and cannot be deleted. Disable it instead.");
        }
        // Carts are transient, so silently drop any lines pointing at the product.
        cartItemRepository.deleteByProductId(id);
        productRepository.delete(product);
    }

    private Product requireWithCategory(Long id) {
        return productRepository.findByIdWithCategory(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", id));
    }

    private Category requireCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> ResourceNotFoundException.of("Category", categoryId));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

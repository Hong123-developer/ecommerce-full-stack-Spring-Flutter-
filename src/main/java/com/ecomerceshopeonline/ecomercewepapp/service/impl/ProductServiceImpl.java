package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.ProductRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.request.VariantRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ProductResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.Category;
import com.ecomerceshopeonline.ecomercewepapp.entity.Product;
import com.ecomerceshopeonline.ecomercewepapp.entity.ProductVariant;
import com.ecomerceshopeonline.ecomercewepapp.exception.BadRequestException;
import com.ecomerceshopeonline.ecomercewepapp.exception.ConflictException;
import com.ecomerceshopeonline.ecomercewepapp.exception.FieldValidationException;
import com.ecomerceshopeonline.ecomercewepapp.exception.ResourceNotFoundException;
import com.ecomerceshopeonline.ecomercewepapp.mapper.ProductMapper;
import com.ecomerceshopeonline.ecomercewepapp.repository.CartItemRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.CategoryRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.OrderItemRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.ProductRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.ProductVariantRepository;
import com.ecomerceshopeonline.ecomercewepapp.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    /** Joins option values into a case-insensitive "same combination" key. */
    private static final String COMBINATION_KEY_SEPARATOR = "\u0000";

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CategoryRepository categoryRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductMapper productMapper;
    private final ProductVariantSync variantSync;

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
                productMapper::toPublicResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getPublic(Long id) {
        Product product = requireWithCategory(id);
        if (!product.isEnabled()) {
            // Hidden products must look absent to shoppers, not "forbidden".
            throw ResourceNotFoundException.of("Product", id);
        }
        return productMapper.toPublicResponse(product);
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
        List<String> optionNames = cleanOptionNames(request.optionNames());
        List<VariantRequest> variantRequests =
                request.variants() == null ? List.of() : request.variants();
        validateVariants(optionNames, variantRequests);
        requireVariantSkusFree(variantRequests, Set.of());

        Product product = productMapper.toEntity(request, category);
        product.getOptionNames().addAll(optionNames);
        for (int i = 0; i < variantRequests.size(); i++) {
            // Ids are meaningless on create - every variant is new.
            ProductVariant variant = ProductVariant.builder().product(product).position(i).build();
            productMapper.applyVariant(variantRequests.get(i), variant);
            product.getVariants().add(variant);
        }
        variantSync.applyToProduct(product);
        return productMapper.toResponse(productRepository.save(product));
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
        Category category = requireCategory(request.categoryId());
        // Older clients send neither field - leave options and variants alone for them.
        List<String> optionNames = request.optionNames() == null
                ? List.copyOf(product.getOptionNames())
                : cleanOptionNames(request.optionNames());

        if (request.variants() == null) {
            // Variants untouched - but they must still fit the (possibly changed) options.
            boolean misfit = product.getVariants().stream()
                    .anyMatch(v -> v.isEnabled() && v.getOptions().size() != optionNames.size());
            if (misfit) {
                throw new FieldValidationException("variants",
                        "Send the variants again when changing the number of options");
            }
            productMapper.apply(request, category, product);
            replaceOptionNames(product, optionNames);
        } else {
            updateVariants(product, request, category, optionNames);
        }

        variantSync.applyToProduct(product);
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
        // Its variants go with it (cascade), none of them can have been ordered.
        cartItemRepository.deleteByProductId(id);
        productRepository.delete(product);
    }

    // ---------- variants ----------

    /**
     * Replaces the product's variants with the requested list. Ids in the request update
     * those variants in place; entries without an id are new. A variant missing from the
     * list is deleted - unless it was ever ordered, in which case it is only disabled so the
     * order history keeps pointing at it. Cart lines for gone or disabled variants are dropped.
     */
    private void updateVariants(Product product, ProductRequest request, Category category,
                                List<String> optionNames) {
        List<VariantRequest> requests = request.variants();
        validateVariants(optionNames, requests);

        Map<Long, ProductVariant> current = new HashMap<>();
        product.getVariants().forEach(v -> current.put(v.getId(), v));
        Set<Long> keptIds = new HashSet<>();
        for (VariantRequest variantRequest : requests) {
            if (variantRequest.id() == null) {
                continue;
            }
            if (!current.containsKey(variantRequest.id())) {
                throw new FieldValidationException("variants",
                        "Variant " + variantRequest.id() + " does not belong to this product");
            }
            keptIds.add(variantRequest.id());
        }

        List<ProductVariant> toDelete = new ArrayList<>();
        List<ProductVariant> toDisable = new ArrayList<>();
        for (ProductVariant variant : product.getVariants()) {
            if (keptIds.contains(variant.getId())) {
                continue;
            }
            if (orderItemRepository.existsByVariantId(variant.getId())) {
                toDisable.add(variant);
            } else {
                toDelete.add(variant);
            }
        }
        // Codes of variants deleted in this same save may be reused by the new list.
        requireVariantSkusFree(requests,
                toDelete.stream().map(ProductVariant::getId).collect(Collectors.toSet()));

        productMapper.apply(request, category, product);
        replaceOptionNames(product, optionNames);

        if (!toDelete.isEmpty()) {
            cartItemRepository.deleteByVariantIdIn(
                    toDelete.stream().map(ProductVariant::getId).toList());
            product.getVariants().removeAll(toDelete);
            variantRepository.deleteAll(toDelete);
            // Deletes must reach the database before inserts that may reuse their codes.
            variantRepository.flush();
        }

        for (int i = 0; i < requests.size(); i++) {
            VariantRequest variantRequest = requests.get(i);
            ProductVariant variant = variantRequest.id() == null
                    ? ProductVariant.builder().product(product).build()
                    : current.get(variantRequest.id());
            productMapper.applyVariant(variantRequest, variant);
            variant.setPosition(i);
            if (variant.getId() == null) {
                variantRepository.save(variant);
                product.getVariants().add(variant);
            }
        }
        // Retired variants go to the end of the admin list, hidden from shoppers.
        for (int i = 0; i < toDisable.size(); i++) {
            toDisable.get(i).setEnabled(false);
            toDisable.get(i).setPosition(requests.size() + i);
        }

        List<Long> disabledIds = product.getVariants().stream()
                .filter(v -> !v.isEnabled())
                .map(ProductVariant::getId)
                .toList();
        if (!disabledIds.isEmpty()) {
            cartItemRepository.deleteByVariantIdIn(disabledIds);
        }
        if (product.hasEnabledVariants()) {
            // Lines added while the product had no options can't say which one to ship.
            cartItemRepository.deleteByProductIdAndVariantIsNull(product.getId());
        }
        product.getVariants().sort(Comparator.comparing(ProductVariant::getPosition)
                .thenComparing(ProductVariant::getId));
    }

    /** Trimmed; must differ from each other ignoring case. Bean validation did blank/length/count. */
    private List<String> cleanOptionNames(List<String> names) {
        if (names == null) {
            return List.of();
        }
        List<String> cleaned = names.stream().map(String::trim).toList();
        Set<String> seen = new HashSet<>();
        for (String name : cleaned) {
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                throw new FieldValidationException("optionNames",
                        "Option names must be different from each other");
            }
        }
        return cleaned;
    }

    private void replaceOptionNames(Product product, List<String> optionNames) {
        if (!product.getOptionNames().equals(optionNames)) {
            product.getOptionNames().clear();
            product.getOptionNames().addAll(optionNames);
        }
    }

    /** Checks that need the whole list: option count, duplicate combinations, codes and ids. */
    private void validateVariants(List<String> optionNames, List<VariantRequest> variants) {
        if (variants.isEmpty()) {
            return;
        }
        if (optionNames.isEmpty()) {
            throw new FieldValidationException("optionNames",
                    "Add at least one option (for example Storage) before adding variants");
        }
        Set<String> combinations = new HashSet<>();
        Set<String> skus = new HashSet<>();
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < variants.size(); i++) {
            VariantRequest variant = variants.get(i);
            if (variant.options().size() != optionNames.size()) {
                throw new FieldValidationException("variants",
                        "Variant " + (i + 1) + " needs one value for each option ("
                                + String.join(", ", optionNames) + ")");
            }
            List<String> values = variant.options().stream().map(String::trim).toList();
            String key = values.stream()
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .collect(Collectors.joining(COMBINATION_KEY_SEPARATOR));
            if (!combinations.add(key)) {
                throw new FieldValidationException("variants",
                        "Two variants have the same options: "
                                + String.join(ProductVariant.LABEL_SEPARATOR, values));
            }
            String sku = variant.sku().trim();
            if (!skus.add(sku.toLowerCase(Locale.ROOT))) {
                throw new FieldValidationException("variants",
                        "Variant code " + sku + " is already used");
            }
            if (variant.id() != null && !ids.add(variant.id())) {
                throw new FieldValidationException("variants",
                        "Variant " + variant.id() + " is listed twice");
            }
        }
    }

    /**
     * Variant codes are unique across the whole shop. A code may stay on the variant that
     * already has it, or be taken over from a variant this same save deletes; anything else
     * (including swapping codes between two variants) is a conflict.
     */
    private void requireVariantSkusFree(List<VariantRequest> variants, Set<Long> beingDeleted) {
        if (variants.isEmpty()) {
            return;
        }
        Map<String, VariantRequest> bySku = new HashMap<>();
        variants.forEach(v -> bySku.put(v.sku().trim().toLowerCase(Locale.ROOT), v));
        for (ProductVariant holder : variantRepository.findBySkuLowerIn(bySku.keySet())) {
            VariantRequest claimant = bySku.get(holder.getSku().toLowerCase(Locale.ROOT));
            boolean sameVariant = claimant != null && holder.getId().equals(claimant.id());
            if (!sameVariant && !beingDeleted.contains(holder.getId())) {
                throw new ConflictException("Variant code " + holder.getSku() + " is already used");
            }
        }
    }

    // ---------- helpers ----------

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

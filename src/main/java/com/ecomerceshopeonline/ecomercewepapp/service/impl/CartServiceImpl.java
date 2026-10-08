package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.AddToCartRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.request.UpdateCartItemRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.CartResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.Cart;
import com.ecomerceshopeonline.ecomercewepapp.entity.CartItem;
import com.ecomerceshopeonline.ecomercewepapp.entity.Product;
import com.ecomerceshopeonline.ecomercewepapp.entity.ProductVariant;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.exception.BadRequestException;
import com.ecomerceshopeonline.ecomercewepapp.exception.ResourceNotFoundException;
import com.ecomerceshopeonline.ecomercewepapp.mapper.CartMapper;
import com.ecomerceshopeonline.ecomercewepapp.repository.CartItemRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.CartRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.ProductRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.UserRepository;
import com.ecomerceshopeonline.ecomercewepapp.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CartMapper cartMapper;
    private final PlatformTransactionManager transactionManager;

    @Override
    @Transactional
    public CartResponse getCart(Long userId) {
        return cartMapper.toResponse(getOrCreate(userId));
    }

    /**
     * Not {@code @Transactional} itself: when two requests add the same new line at once, the
     * loser's INSERT hits the unique constraint / index and its transaction is unusable
     * (PostgreSQL aborts it). So each attempt runs in its own transaction and the loser simply
     * retries once - by then it sees the winner's line and tops up its quantity instead.
     */
    @Override
    public CartResponse addItem(Long userId, AddToCartRequest request) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            return tx.execute(status -> doAddItem(userId, request));
        } catch (DataIntegrityViolationException raced) {
            return tx.execute(status -> doAddItem(userId, request));
        }
    }

    private CartResponse doAddItem(Long userId, AddToCartRequest request) {
        Cart cart = getOrCreate(userId);
        Product product = requirePurchasableProduct(request.productId());
        ProductVariant variant = resolveVariant(product, request.variantId());
        Long variantId = variant == null ? null : variant.getId();

        CartItem existing = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .filter(item -> Objects.equals(variantIdOf(item), variantId))
                .findFirst()
                .orElse(null);

        // Adding the same product (and option) again tops up the existing line instead of
        // duplicating it - one line per (product, variant).
        int newQuantity = (existing == null ? 0 : existing.getQuantity()) + request.quantity();
        requireStock(product, variant, newQuantity);

        if (existing == null) {
            CartItem item = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .variant(variant)
                    .quantity(request.quantity())
                    .build();
            cart.getItems().add(item);
            // saveAndFlush so a duplicate-line violation surfaces here, inside the attempt.
            cartItemRepository.saveAndFlush(item);
        } else {
            existing.setQuantity(newQuantity);
            cartItemRepository.save(existing);
        }

        return cartMapper.toResponse(reload(userId));
    }

    @Override
    @Transactional
    public CartResponse updateItem(Long userId, Long itemId, UpdateCartItemRequest request) {
        Cart cart = getOrCreate(userId);
        CartItem item = requireOwnedItem(cart, itemId);

        if (request.quantity() == 0) {
            cart.getItems().remove(item);
            cartItemRepository.delete(item);
            return cartMapper.toResponse(reload(userId));
        }

        requireStock(item.getProduct(), item.getVariant(), request.quantity());
        item.setQuantity(request.quantity());
        cartItemRepository.save(item);
        return cartMapper.toResponse(reload(userId));
    }

    @Override
    @Transactional
    public CartResponse removeItem(Long userId, Long itemId) {
        Cart cart = getOrCreate(userId);
        CartItem item = requireOwnedItem(cart, itemId);
        cart.getItems().remove(item);
        cartItemRepository.delete(item);
        return cartMapper.toResponse(reload(userId));
    }

    @Override
    @Transactional
    public void clear(Long userId) {
        Cart cart = getOrCreate(userId);
        cart.getItems().clear();
        cartItemRepository.deleteByCartId(cart.getId());
    }

    @Override
    @Transactional
    public Cart requireCartEntity(Long userId) {
        return getOrCreate(userId);
    }

    // ---------- helpers ----------

    private Cart getOrCreate(Long userId) {
        return cartRepository.findByUserIdWithItems(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
            return cartRepository.save(Cart.builder().user(user).build());
        });
    }

    private Cart reload(Long userId) {
        return cartRepository.findByUserIdWithItems(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));
    }

    /** Scoping the lookup through the user's own cart is what prevents editing someone else's line. */
    private CartItem requireOwnedItem(Cart cart, Long itemId) {
        return cart.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("Cart item", itemId));
    }

    private Product requirePurchasableProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", productId));
        if (!product.isEnabled()) {
            throw new BadRequestException("'" + product.getName() + "' is no longer available");
        }
        return product;
    }

    /**
     * A product with enabled variants can only be bought as one of them; a plain product takes
     * no variant id (sending one for it is rejected).
     */
    private ProductVariant resolveVariant(Product product, Long variantId) {
        if (variantId == null) {
            if (product.hasEnabledVariants()) {
                throw new BadRequestException("Please choose an option");
            }
            return null;
        }
        ProductVariant variant = product.getVariants().stream()
                .filter(v -> v.getId().equals(variantId))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "That option does not belong to '" + product.getName() + "'"));
        if (!variant.isEnabled()) {
            throw new BadRequestException("'" + displayName(product, variant) + "' is no longer available");
        }
        return variant;
    }

    private void requireStock(Product product, ProductVariant variant, int wanted) {
        boolean enough = variant == null ? product.isInStock(wanted) : variant.isInStock(wanted);
        if (!enough) {
            int left = variant == null ? product.getStockQuantity() : variant.getStockQuantity();
            throw new BadRequestException("Only " + left
                    + " of '" + displayName(product, variant) + "' left in stock");
        }
    }

    private static Long variantIdOf(CartItem item) {
        return item.getVariant() == null ? null : item.getVariant().getId();
    }

    private static String displayName(Product product, ProductVariant variant) {
        return variant == null ? product.getName() : product.getName() + " (" + variant.getLabel() + ")";
    }
}

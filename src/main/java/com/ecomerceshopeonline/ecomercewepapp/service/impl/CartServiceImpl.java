package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.AddToCartRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.request.UpdateCartItemRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.CartResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.Cart;
import com.ecomerceshopeonline.ecomercewepapp.entity.CartItem;
import com.ecomerceshopeonline.ecomercewepapp.entity.Product;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CartMapper cartMapper;

    @Override
    @Transactional
    public CartResponse getCart(Long userId) {
        return cartMapper.toResponse(getOrCreate(userId));
    }

    @Override
    @Transactional
    public CartResponse addItem(Long userId, AddToCartRequest request) {
        Cart cart = getOrCreate(userId);
        Product product = requirePurchasableProduct(request.productId());

        CartItem existing = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .findFirst()
                .orElse(null);

        // Adding the same product again tops up the existing line instead of duplicating it -
        // the (cart_id, product_id) unique constraint would reject a second row anyway.
        int newQuantity = (existing == null ? 0 : existing.getQuantity()) + request.quantity();
        requireStock(product, newQuantity);

        if (existing == null) {
            CartItem item = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(request.quantity())
                    .build();
            cart.getItems().add(item);
            cartItemRepository.save(item);
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

        requireStock(item.getProduct(), request.quantity());
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

    private void requireStock(Product product, int wanted) {
        if (!product.isInStock(wanted)) {
            throw new BadRequestException("Only " + product.getStockQuantity()
                    + " of '" + product.getName() + "' left in stock");
        }
    }
}

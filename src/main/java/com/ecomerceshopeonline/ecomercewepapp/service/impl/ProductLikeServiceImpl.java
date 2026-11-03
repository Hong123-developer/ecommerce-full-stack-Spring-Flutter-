package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ProductLikeResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.Product;
import com.ecomerceshopeonline.ecomercewepapp.entity.ProductLike;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.exception.ResourceNotFoundException;
import com.ecomerceshopeonline.ecomercewepapp.repository.ProductLikeRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.ProductRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.UserRepository;
import com.ecomerceshopeonline.ecomercewepapp.service.ProductLikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductLikeServiceImpl implements ProductLikeService {

    private final ProductLikeRepository likeRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ProductLikeResponse toggle(Long userId, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", productId));

        var existing = likeRepository.findByUserIdAndProductId(userId, productId);
        boolean nowLiked;

        if (existing.isPresent()) {
            // Unlike
            likeRepository.delete(existing.get());
            likeRepository.flush();          // so the count query below sees the deletion
            nowLiked = false;
        } else {
            // Like
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
            ProductLike like = new ProductLike();
            like.setUser(user);
            like.setProduct(product);
            likeRepository.save(like);
            likeRepository.flush();
            nowLiked = true;
        }

        long count = likeRepository.countByProductId(productId);
        return toResponse(product, nowLiked, count,
                nowLiked ? java.time.LocalDateTime.now() : null);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isLiked(Long userId, Long productId) {
        return likeRepository.existsByUserIdAndProductId(userId, productId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countLikes(Long productId) {
        return likeRepository.countByProductId(productId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductLikeResponse> myLikes(Long userId, Pageable pageable) {
        return PageResponse.from(
                likeRepository.findByUserIdWithProduct(userId, pageable),
                like -> toResponse(
                        like.getProduct(),
                        true,
                        likeRepository.countByProductId(like.getProduct().getId()),
                        like.getCreatedAt()));
    }

    private ProductLikeResponse toResponse(Product product, boolean liked, long likeCount,
                                           java.time.LocalDateTime likedAt) {
        return new ProductLikeResponse(
                product.getId(),
                product.getName(),
                product.discountedPrice(product.getPrice()),
                product.getImageUrl(),
                product.isInStock(1),
                liked,
                likeCount,
                likedAt);
    }
}

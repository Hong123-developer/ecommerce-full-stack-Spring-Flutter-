package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.ReviewRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.AdminReviewResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.MyReviewResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ReviewResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ReviewSummaryResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.OrderItem;
import com.ecomerceshopeonline.ecomercewepapp.entity.Product;
import com.ecomerceshopeonline.ecomercewepapp.entity.ProductReview;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import com.ecomerceshopeonline.ecomercewepapp.exception.ApiException;
import com.ecomerceshopeonline.ecomercewepapp.exception.BadRequestException;
import com.ecomerceshopeonline.ecomercewepapp.exception.ConflictException;
import com.ecomerceshopeonline.ecomercewepapp.exception.ResourceNotFoundException;
import com.ecomerceshopeonline.ecomercewepapp.exception.UnauthorizedException;
import com.ecomerceshopeonline.ecomercewepapp.mapper.ReviewMapper;
import com.ecomerceshopeonline.ecomercewepapp.repository.OrderItemRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.ProductRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.ProductReviewRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.UserRepository;
import com.ecomerceshopeonline.ecomercewepapp.service.ProductReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reviews and the product rating derived from them.
 * <p>
 * Every change takes a row lock on the product first and then recomputes
 * {@code ratingAverage} / {@code ratingCount} from the visible reviews in the same transaction,
 * so two reviews of one product written at the same moment cannot overwrite each other's
 * count (the second waits, then sees the first's committed row).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductReviewServiceImpl implements ProductReviewService {

    static final int MAX_PAGE_SIZE = 50;
    static final String NOT_ELIGIBLE = "Only customers who received this product can review it";
    static final String ALREADY_REVIEWED = "You already reviewed this product";
    static final String NOT_AUTHOR = "You can only change your own review";

    /** Orders that may still be delivered - "you can review once it arrives". */
    private static final Set<OrderStatus> IN_PROGRESS = EnumSet.of(
            OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.SHIPPED);

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ReviewMapper reviewMapper;

    // ---------------------------------------------------------------- storefront

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> list(Long productId, Integer rating, String sort, int page, int size,
                                             Long viewerId) {
        requireShopperVisible(productId);
        requireStarValue(rating);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), publicSort(sort));
        return PageResponse.from(reviewRepository.findVisible(productId, rating, pageable),
                review -> reviewMapper.toResponse(review, viewerId));
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewSummaryResponse summary(Long productId) {
        requireShopperVisible(productId);
        RatingStats stats = stats(productId);
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (int star = ProductReview.MAX_RATING; star >= ProductReview.MIN_RATING; star--) {
            distribution.put(String.valueOf(star), stats.perStar()[star]);
        }
        return new ReviewSummaryResponse(stats.average(), stats.count(), distribution);
    }

    @Override
    @Transactional(readOnly = true)
    public MyReviewResponse mine(Long productId, Long userId, boolean admin) {
        requireShopperVisible(productId);
        Optional<ProductReview> own = reviewRepository.findByProductIdAndUserId(productId, userId);
        if (own.isPresent()) {
            return new MyReviewResponse(false, MyReviewResponse.Reason.ALREADY_REVIEWED,
                    reviewMapper.toResponse(own.get(), userId));
        }
        MyReviewResponse.Reason reason = admin ? MyReviewResponse.Reason.NOT_PURCHASED : ineligibility(productId, userId);
        return new MyReviewResponse(reason == null, reason, null);
    }

    @Override
    @Transactional
    public ReviewResponse create(Long productId, Long userId, boolean admin, ReviewRequest request) {
        Product product = productRepository.findByIdForUpdate(productId)
                .filter(Product::isEnabled)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", productId));
        if (admin) {
            throw new ApiException(NOT_ELIGIBLE, HttpStatus.FORBIDDEN);
        }
        if (reviewRepository.existsByProductIdAndUserId(productId, userId)) {
            throw new ConflictException(ALREADY_REVIEWED);
        }
        List<OrderItem> received = orderItemRepository.findPurchasedLines(
                userId, productId, OrderStatus.DELIVERED, PageRequest.of(0, 1));
        if (received.isEmpty()) {
            throw new ApiException(NOT_ELIGIBLE, HttpStatus.FORBIDDEN);
        }
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Please sign in again"));

        ProductReview review = reviewRepository.save(ProductReview.builder()
                .product(product)
                .user(author)
                .rating(request.rating())
                .title(request.title())
                .comment(request.comment())
                .variantLabel(received.getFirst().getVariantLabel())
                .build());
        refreshRating(productId);
        log.info("Customer {} reviewed product {} ({} stars)", userId, productId, review.getRating());
        return reviewMapper.toResponse(review, userId);
    }

    @Override
    @Transactional
    public ReviewResponse update(Long reviewId, Long userId, ReviewRequest request) {
        ProductReview review = lockAndLoad(reviewId);
        requireAuthor(review, userId);
        review.setRating(request.rating());
        review.setTitle(request.title());
        review.setComment(request.comment());
        reviewRepository.save(review);
        refreshRating(review.getProduct().getId());
        return reviewMapper.toResponse(review, userId);
    }

    @Override
    @Transactional
    public void delete(Long reviewId, Long userId) {
        ProductReview review = lockAndLoad(reviewId);
        requireAuthor(review, userId);
        Long productId = review.getProduct().getId();
        reviewRepository.delete(review);
        refreshRating(productId);
    }

    // ---------------------------------------------------------------- admin

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminReviewResponse> adminList(String search, Integer rating, Boolean visible,
                                                       Pageable pageable) {
        requireStarValue(rating);
        return PageResponse.from(
                reviewRepository.adminSearch(blankToNull(search), rating, visible, withTiebreaker(pageable)),
                reviewMapper::toAdminResponse);
    }

    @Override
    @Transactional
    public AdminReviewResponse setVisible(Long reviewId, boolean visible) {
        Long productId = lockProductOf(reviewId);
        if (reviewRepository.updateVisible(reviewId, visible) == 0) {
            throw ResourceNotFoundException.of("Review", reviewId);
        }
        refreshRating(productId);
        log.info("Review {} of product {} {}", reviewId, productId, visible ? "shown" : "hidden");
        return reviewMapper.toAdminResponse(reviewRepository.findDetailById(reviewId)
                .orElseThrow(() -> ResourceNotFoundException.of("Review", reviewId)));
    }

    @Override
    @Transactional
    public void adminDelete(Long reviewId) {
        Long productId = lockProductOf(reviewId);
        if (reviewRepository.bulkDeleteById(reviewId) == 0) {
            throw ResourceNotFoundException.of("Review", reviewId);
        }
        refreshRating(productId);
        log.info("Review {} of product {} deleted by an admin", reviewId, productId);
    }

    // ---------------------------------------------------------------- housekeeping

    @Override
    @Transactional
    public void deleteAllByUser(Long userId) {
        List<Long> productIds = reviewRepository.findProductIdsByUserId(userId).stream().sorted().toList();
        if (productIds.isEmpty()) {
            return;
        }
        // Ascending id, like checkout, so two of these can never wait on each other.
        productIds.forEach(productRepository::findByIdForUpdate);
        reviewRepository.bulkDeleteByUserId(userId);
        productIds.forEach(this::refreshRating);
    }

    // ---------------------------------------------------------------- rating

    /** Counts per star (index 1..5) over the visible reviews, their total and average. */
    private record RatingStats(long[] perStar, long count, BigDecimal average) {
    }

    private RatingStats stats(Long productId) {
        long[] perStar = new long[ProductReview.MAX_RATING + 1];
        long count = 0;
        long sum = 0;
        for (Object[] row : reviewRepository.countVisibleByRating(productId)) {
            int star = ((Number) row[0]).intValue();
            long reviews = ((Number) row[1]).longValue();
            if (star < ProductReview.MIN_RATING || star > ProductReview.MAX_RATING) {
                continue;
            }
            perStar[star] = reviews;
            count += reviews;
            sum += star * reviews;
        }
        BigDecimal average = count == 0
                ? BigDecimal.ZERO.setScale(1)
                : BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
        return new RatingStats(perStar, count, average);
    }

    /** Re-derives the product's rating; the caller holds the product's row lock. */
    private void refreshRating(Long productId) {
        reviewRepository.flush();
        RatingStats stats = stats(productId);
        productRepository.updateRating(productId, stats.average(), Math.toIntExact(stats.count()));
    }

    // ---------------------------------------------------------------- helpers

    /** Locks the review's product row, then reads the review fresh. */
    private ProductReview lockAndLoad(Long reviewId) {
        lockProductOf(reviewId);
        return reviewRepository.findDetailById(reviewId)
                .orElseThrow(() -> ResourceNotFoundException.of("Review", reviewId));
    }

    private Long lockProductOf(Long reviewId) {
        Long productId = reviewRepository.findProductIdById(reviewId)
                .orElseThrow(() -> ResourceNotFoundException.of("Review", reviewId));
        productRepository.findByIdForUpdate(productId);
        return productId;
    }

    private MyReviewResponse.Reason ineligibility(Long productId, Long userId) {
        if (orderItemRepository.existsPurchase(userId, productId, EnumSet.of(OrderStatus.DELIVERED))) {
            return null;
        }
        if (orderItemRepository.existsPurchase(userId, productId, IN_PROGRESS)) {
            return MyReviewResponse.Reason.NOT_DELIVERED;
        }
        // Never ordered, or only in orders that were cancelled / refunded.
        return MyReviewResponse.Reason.NOT_PURCHASED;
    }

    /** Hidden (disabled) products must look absent to shoppers, as on the product page. */
    private void requireShopperVisible(Long productId) {
        productRepository.findById(productId)
                .filter(Product::isEnabled)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", productId));
    }

    private static void requireAuthor(ProductReview review, Long userId) {
        if (!Objects.equals(review.getUser().getId(), userId)) {
            throw new ApiException(NOT_AUTHOR, HttpStatus.FORBIDDEN);
        }
    }

    private static void requireStarValue(Integer rating) {
        if (rating != null && (rating < ProductReview.MIN_RATING || rating > ProductReview.MAX_RATING)) {
            throw new BadRequestException("rating must be between 1 and 5");
        }
    }

    /** newest (default, also for anything unknown) | highest | lowest; ties newest first. */
    private static Sort publicSort(String sort) {
        Sort newest = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        String key = sort == null ? "" : sort.strip().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "highest" -> Sort.by(Sort.Order.desc("rating")).and(newest);
            case "lowest" -> Sort.by(Sort.Order.asc("rating")).and(newest);
            default -> newest;
        };
    }

    /** Ends every admin sort with the id (newest first) so ties keep one order across pages. */
    private static Pageable withTiebreaker(Pageable pageable) {
        if (pageable.isUnpaged() || pageable.getSort().getOrderFor("id") != null) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                pageable.getSort().and(Sort.by(Sort.Direction.DESC, "id")));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

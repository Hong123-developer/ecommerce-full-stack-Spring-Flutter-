import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:cached_network_image/cached_network_image.dart';

import '../../config/theme.dart';
import '../../models/models.dart';
import '../../providers/providers.dart';
import '../../widgets/common_widgets.dart';

class ProductDetailScreen extends ConsumerStatefulWidget {
  final int productId;

  const ProductDetailScreen({super.key, required this.productId});

  @override
  ConsumerState<ProductDetailScreen> createState() =>
      _ProductDetailScreenState();
}

class _ProductDetailScreenState extends ConsumerState<ProductDetailScreen> {
  int _selectedImageIndex = 0;
  int _quantity = 1;
  ProductVariant? _selectedVariant;
  bool _isAddingToCart = false;

  @override
  Widget build(BuildContext context) {
    final productAsync = ref.watch(productDetailProvider(widget.productId));
    final cart = ref.watch(cartProvider);
    final language = ref.watch(appLanguageProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new_rounded),
          onPressed: () {
            if (context.canPop()) {
              context.pop();
            } else {
              context.go('/home');
            }
          },
        ),
        title: Text(appText(language, 'productDetails')),
        actions: [
          productAsync.maybeWhen(
            data: (product) => Padding(
              padding: const EdgeInsets.only(right: 4),
              child: WishlistButton(
                productId: product.id,
                initialLiked: product.liked,
                size: 20,
              ),
            ),
            orElse: () => const SizedBox.shrink(),
          ),
          _buildCartIcon(cart.totalQuantity),
          const SizedBox(width: 8),
        ],
      ),
      body: productAsync.when(
        data: (product) => _buildContent(product),
        loading: () => const Center(
          child: CircularProgressIndicator(color: AppColors.primary),
        ),
        error: (err, stack) => Center(
          child: EmptyState(
            icon: Icons.error_outline_rounded,
            title: appText(language, 'failedLoadProduct'),
            subtitle: err.toString(),
            actionLabel: appText(language, 'retry'),
            onAction: () =>
                ref.refresh(productDetailProvider(widget.productId)),
          ),
        ),
      ),
      bottomNavigationBar: productAsync.maybeWhen(
        data: (product) => _buildBottomBar(product),
        orElse: () => const SizedBox.shrink(),
      ),
    );
  }

  Widget _buildContent(Product product) {
    final images = <String>[];
    if (product.imageUrl != null && product.imageUrl!.isNotEmpty) {
      images.add(product.imageUrl!);
    }
    for (final url in product.galleryUrls) {
      if (!images.contains(url)) images.add(url);
    }
    if (images.isEmpty) images.add('');

    // Default variant if not chosen
    if (_selectedVariant == null && product.variants.isNotEmpty) {
      _selectedVariant = product.variants.first;
    }

    final currentPrice = _selectedVariant?.price ?? product.price;
    final originalPrice =
        _selectedVariant?.originalPrice ?? product.originalPrice;
    final discountPercent = product.discountPercent;
    final inStock = _selectedVariant != null
        ? _selectedVariant!.inStock
        : product.inStock;
    final availableStock = _selectedVariant != null
        ? _selectedVariant!.stockQuantity
        : product.stockQuantity;

    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // ─── Image Gallery Carousel ───
          _buildImageGallery(images),

          // ─── Main Details Card ───
          Container(
            padding: const EdgeInsets.fromLTRB(20, 24, 20, 28),
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.surface,
              borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Category & Stock Status Row
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    if (product.categoryName != null)
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 10,
                          vertical: 4,
                        ),
                        decoration: BoxDecoration(
                          color: AppColors.primary.withValues(alpha: 0.1),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Text(
                          product.categoryName!,
                          style: const TextStyle(
                            color: AppColors.primary,
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      )
                    else
                      const SizedBox.shrink(),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 10,
                        vertical: 4,
                      ),
                      decoration: BoxDecoration(
                        color: inStock
                            ? AppColors.success.withValues(alpha: 0.1)
                            : AppColors.error.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            inStock
                                ? Icons.check_circle_rounded
                                : Icons.cancel_rounded,
                            size: 14,
                            color: inStock
                                ? AppColors.success
                                : AppColors.error,
                          ),
                          const SizedBox(width: 4),
                          Text(
                            inStock
                                ? 'In Stock ($availableStock)'
                                : 'Out of Stock',
                            style: TextStyle(
                              color: inStock
                                  ? AppColors.success
                                  : AppColors.error,
                              fontSize: 12,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),

                // Title
                Text(
                  product.name,
                  style: const TextStyle(
                    fontSize: 22,
                    fontWeight: FontWeight.w700,
                    color: AppColors.textPrimary,
                    height: 1.25,
                  ),
                ),
                const SizedBox(height: 8),

                // Rating & SKU
                Row(
                  children: [
                    ...List.generate(5, (index) {
                      return Icon(
                        index < product.rating.round()
                            ? Icons.star_rounded
                            : Icons.star_outline_rounded,
                        size: 18,
                        color: index < product.rating.round()
                            ? AppColors.starFilled
                            : AppColors.starEmpty,
                      );
                    }),
                    const SizedBox(width: 6),
                    Text(
                      product.rating.toStringAsFixed(1),
                      style: const TextStyle(
                        fontWeight: FontWeight.w700,
                        fontSize: 14,
                        color: AppColors.textPrimary,
                      ),
                    ),
                    const SizedBox(width: 4),
                    Text(
                      '(${product.reviewCount} reviews)',
                      style: const TextStyle(
                        color: AppColors.textSecondary,
                        fontSize: 13,
                      ),
                    ),
                    const Spacer(),
                    if (product.sku != null)
                      Text(
                        'SKU: ${product.sku}',
                        style: const TextStyle(
                          color: AppColors.textHint,
                          fontSize: 12,
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 12),

                // Likes & Wishlist info badge
                _buildWishlistBadge(product),
                const SizedBox(height: 16),

                // Price
                Row(
                  crossAxisAlignment: CrossAxisAlignment.baseline,
                  textBaseline: TextBaseline.alphabetic,
                  children: [
                    Row(
                      children: [
                        Text(
                          '\$${currentPrice.toStringAsFixed(2)}',
                          style: const TextStyle(
                            fontSize: 28,
                            fontWeight: FontWeight.w800,
                            color: AppColors.primary,
                          ),
                        ),
                        if (discountPercent > 0 &&
                            originalPrice > currentPrice) ...[
                          const SizedBox(width: 10),
                          Text(
                            '\$${originalPrice.toStringAsFixed(2)}',
                            style: const TextStyle(
                              fontSize: 14,
                              color: AppColors.textSecondary,
                              decoration: TextDecoration.lineThrough,
                            ),
                          ),
                          const SizedBox(width: 6),
                          Text(
                            '-$discountPercent%',
                            style: const TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.w700,
                              color: AppColors.accent,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ],
                ),
                const SizedBox(height: 18),
                _buildProductBenefits(),
                const Divider(height: 32, color: AppColors.divider),

                // ─── Variants Selection ───
                if (product.variants.isNotEmpty) ...[
                  const Text(
                    'Select Option',
                    style: TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w700,
                      color: AppColors.textPrimary,
                    ),
                  ),
                  const SizedBox(height: 10),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: product.variants.map((variant) {
                      final isSelected = _selectedVariant?.id == variant.id;
                      return ChoiceChip(
                        label: Text(
                          variant.label.isNotEmpty
                              ? variant.label
                              : variant.options.join(' / '),
                        ),
                        selected: isSelected,
                        onSelected: (selected) {
                          if (selected) {
                            setState(() {
                              _selectedVariant = variant;
                              if (_quantity > variant.stockQuantity) {
                                _quantity = variant.stockQuantity > 0 ? 1 : 0;
                              }
                            });
                          }
                        },
                        selectedColor: AppColors.primary,
                        labelStyle: TextStyle(
                          color: isSelected
                              ? Colors.white
                              : AppColors.textPrimary,
                          fontWeight: isSelected
                              ? FontWeight.w600
                              : FontWeight.w500,
                        ),
                        backgroundColor: AppColors.surfaceVariant,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(10),
                        ),
                        side: BorderSide.none,
                      );
                    }).toList(),
                  ),
                  const SizedBox(height: 20),
                ],

                // ─── Quantity Selector ───
                Row(
                  children: [
                    const Text(
                      'Quantity',
                      style: TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w700,
                        color: AppColors.textPrimary,
                      ),
                    ),
                    const Spacer(),
                    Container(
                      decoration: BoxDecoration(
                        color: AppColors.surfaceVariant,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Row(
                        children: [
                          IconButton(
                            icon: const Icon(Icons.remove, size: 18),
                            onPressed: _quantity > 1
                                ? () => setState(() => _quantity--)
                                : null,
                            color: AppColors.textPrimary,
                          ),
                          Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 8),
                            child: Text(
                              '$_quantity',
                              style: const TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.w700,
                                color: AppColors.textPrimary,
                              ),
                            ),
                          ),
                          IconButton(
                            icon: const Icon(Icons.add, size: 18),
                            onPressed: inStock && _quantity < availableStock
                                ? () => setState(() => _quantity++)
                                : null,
                            color: AppColors.textPrimary,
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const Divider(height: 32, color: AppColors.divider),

                // ─── Description ───
                const Text(
                  'Description',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: AppColors.textPrimary,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  product.description != null && product.description!.isNotEmpty
                      ? product.description!
                      : 'No description available for this product.',
                  style: const TextStyle(
                    fontSize: 14,
                    height: 1.5,
                    color: AppColors.textSecondary,
                  ),
                ),
                const Divider(height: 32, color: AppColors.divider),

                // ─── Customer Reviews Section ───
                _buildReviewsSection(product),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildImageGallery(List<String> images) {
    final scheme = Theme.of(context).colorScheme;
    return SizedBox(
      height: 370,
      child: Stack(
        children: [
          Container(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: [scheme.surfaceContainerHighest, scheme.surface],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
            ),
            child: PageView.builder(
              itemCount: images.length,
              onPageChanged: (i) => setState(() => _selectedImageIndex = i),
              itemBuilder: (context, index) {
                final url = images[index];
                return Padding(
                  padding: const EdgeInsets.fromLTRB(24, 18, 24, 72),
                  child: url.isNotEmpty
                      ? CachedNetworkImage(
                          imageUrl: url,
                          fit: BoxFit.contain,
                          placeholder: (_, __) => const Center(
                            child: CircularProgressIndicator(
                              color: AppColors.primary,
                            ),
                          ),
                          errorWidget: (_, __, ___) => const Center(
                            child: Icon(
                              Icons.image_not_supported_rounded,
                              size: 60,
                              color: AppColors.textHint,
                            ),
                          ),
                        )
                      : const Center(
                          child: Icon(
                            Icons.image_not_supported_rounded,
                            size: 60,
                            color: AppColors.textHint,
                          ),
                        ),
                );
              },
            ),
          ),
          if (images.length > 1)
            Positioned(
              bottom: 16,
              left: 0,
              right: 0,
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: List.generate(images.length, (index) {
                  final isSelected = _selectedImageIndex == index;
                  return GestureDetector(
                    onTap: () => setState(() => _selectedImageIndex = index),
                    child: AnimatedContainer(
                      duration: const Duration(milliseconds: 200),
                      margin: const EdgeInsets.symmetric(horizontal: 4),
                      width: isSelected ? 24 : 8,
                      height: 8,
                      decoration: BoxDecoration(
                        color: isSelected
                            ? scheme.primary
                            : scheme.outline.withValues(alpha: 0.45),
                        borderRadius: BorderRadius.circular(4),
                      ),
                    ),
                  );
                }),
              ),
            ),
          Positioned(
            top: 16,
            right: 16,
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: scheme.surface.withValues(alpha: 0.85),
                borderRadius: BorderRadius.circular(20),
              ),
              child: Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: 10,
                  vertical: 6,
                ),
                child: Text(
                  '${_selectedImageIndex + 1}/${images.length}',
                  style: TextStyle(
                    color: scheme.onSurface,
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildProductBenefits() {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
      decoration: BoxDecoration(
        color: scheme.surfaceContainerHighest.withValues(alpha: 0.65),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: scheme.outlineVariant.withValues(alpha: 0.55),
        ),
      ),
      child: Row(
        children: [
          _benefitItem(
            icon: Icons.local_shipping_outlined,
            title: 'Fast delivery',
            color: scheme.primary,
          ),
          _benefitDivider(),
          _benefitItem(
            icon: Icons.verified_outlined,
            title: 'Trusted quality',
            color: AppColors.success,
          ),
          _benefitDivider(),
          _benefitItem(
            icon: Icons.lock_outline_rounded,
            title: 'Secure checkout',
            color: scheme.secondary,
          ),
        ],
      ),
    );
  }

  Widget _benefitItem({
    required IconData icon,
    required String title,
    required Color color,
  }) {
    return Expanded(
      child: Column(
        children: [
          Icon(icon, size: 20, color: color),
          const SizedBox(height: 5),
          Text(
            title,
            textAlign: TextAlign.center,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              fontSize: 10,
              fontWeight: FontWeight.w600,
              color: AppColors.textSecondary,
            ),
          ),
        ],
      ),
    );
  }

  Widget _benefitDivider() {
    return Container(width: 1, height: 30, color: AppColors.divider);
  }

  Widget _buildReviewsSection(Product product) {
    final reviewsAsync = ref.watch(reviewsProvider(widget.productId));
    final summaryAsync = ref.watch(reviewSummaryProvider(widget.productId));

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            const Text(
              'Ratings & Reviews',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w700,
                color: AppColors.textPrimary,
              ),
            ),
            if (product.reviewCount > 0)
              Text(
                '${product.reviewCount} total',
                style: const TextStyle(
                  fontSize: 13,
                  color: AppColors.textSecondary,
                ),
              ),
          ],
        ),
        const SizedBox(height: 16),

        // Summary Rating Bar
        summaryAsync.when(
          data: (summary) => Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: AppColors.surfaceVariant,
              borderRadius: BorderRadius.circular(16),
            ),
            child: Row(
              children: [
                Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      summary.average.toStringAsFixed(1),
                      style: const TextStyle(
                        fontSize: 32,
                        fontWeight: FontWeight.w800,
                        color: AppColors.textPrimary,
                      ),
                    ),
                    Row(
                      children: List.generate(5, (i) {
                        return Icon(
                          i < summary.average.round()
                              ? Icons.star_rounded
                              : Icons.star_outline_rounded,
                          size: 16,
                          color: i < summary.average.round()
                              ? AppColors.starFilled
                              : AppColors.starEmpty,
                        );
                      }),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '${summary.count} reviews',
                      style: const TextStyle(
                        fontSize: 11,
                        color: AppColors.textSecondary,
                      ),
                    ),
                  ],
                ),
                const SizedBox(width: 24),
                Expanded(
                  child: Column(
                    children: [5, 4, 3, 2, 1].map((star) {
                      final count = summary.distribution['$star'] ?? 0;
                      final ratio = summary.count > 0
                          ? count / summary.count
                          : 0.0;
                      return Padding(
                        padding: const EdgeInsets.symmetric(vertical: 2),
                        child: Row(
                          children: [
                            Text(
                              '$star',
                              style: const TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            const Icon(
                              Icons.star_rounded,
                              size: 12,
                              color: AppColors.starFilled,
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              child: ClipRRect(
                                borderRadius: BorderRadius.circular(4),
                                child: LinearProgressIndicator(
                                  value: ratio,
                                  backgroundColor: AppColors.divider,
                                  valueColor: const AlwaysStoppedAnimation(
                                    AppColors.primary,
                                  ),
                                  minHeight: 6,
                                ),
                              ),
                            ),
                            const SizedBox(width: 8),
                            Text(
                              '$count',
                              style: const TextStyle(
                                fontSize: 11,
                                color: AppColors.textSecondary,
                              ),
                            ),
                          ],
                        ),
                      );
                    }).toList(),
                  ),
                ),
              ],
            ),
          ),
          loading: () => const ShimmerLoading(height: 80),
          error: (_, __) => const SizedBox.shrink(),
        ),
        const SizedBox(height: 16),

        // List of Reviews
        reviewsAsync.when(
          data: (page) {
            if (page.content.isEmpty) {
              return const Padding(
                padding: EdgeInsets.symmetric(vertical: 16),
                child: Center(
                  child: Text(
                    'No reviews yet. Be the first to review!',
                    style: TextStyle(
                      color: AppColors.textSecondary,
                      fontSize: 13,
                    ),
                  ),
                ),
              );
            }
            return Column(
              children: page.content.map((review) {
                return Container(
                  margin: const EdgeInsets.only(bottom: 12),
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceVariant.withValues(alpha: 0.5),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          CircleAvatar(
                            radius: 16,
                            backgroundColor: AppColors.primary.withValues(
                              alpha: 0.1,
                            ),
                            child: Text(
                              review.authorName.isNotEmpty
                                  ? review.authorName[0].toUpperCase()
                                  : 'U',
                              style: const TextStyle(
                                fontWeight: FontWeight.w700,
                                color: AppColors.primary,
                                fontSize: 13,
                              ),
                            ),
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  children: [
                                    Text(
                                      review.authorName,
                                      style: const TextStyle(
                                        fontWeight: FontWeight.w600,
                                        fontSize: 13,
                                      ),
                                    ),
                                    if (review.verifiedPurchase) ...[
                                      const SizedBox(width: 6),
                                      const Icon(
                                        Icons.verified_rounded,
                                        size: 14,
                                        color: AppColors.success,
                                      ),
                                    ],
                                  ],
                                ),
                                if (review.createdAt != null)
                                  Text(
                                    review.createdAt!.split('T').first,
                                    style: const TextStyle(
                                      color: AppColors.textHint,
                                      fontSize: 11,
                                    ),
                                  ),
                              ],
                            ),
                          ),
                          Row(
                            children: List.generate(5, (i) {
                              return Icon(
                                i < review.rating
                                    ? Icons.star_rounded
                                    : Icons.star_outline_rounded,
                                size: 14,
                                color: i < review.rating
                                    ? AppColors.starFilled
                                    : AppColors.starEmpty,
                              );
                            }),
                          ),
                        ],
                      ),
                      if (review.comment != null &&
                          review.comment!.isNotEmpty) ...[
                        const SizedBox(height: 8),
                        Text(
                          review.comment!,
                          style: const TextStyle(
                            fontSize: 13,
                            color: AppColors.textSecondary,
                            height: 1.4,
                          ),
                        ),
                      ],
                    ],
                  ),
                );
              }).toList(),
            );
          },
          loading: () => const ShimmerLoading(height: 100),
          error: (_, __) => const SizedBox.shrink(),
        ),
      ],
    );
  }

  Widget _buildBottomBar(Product product) {
    final inStock = _selectedVariant != null
        ? _selectedVariant!.inStock
        : product.inStock;

    return Container(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
      decoration: BoxDecoration(
        color: AppColors.surface,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.05),
            blurRadius: 10,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: SafeArea(
        top: false,
        child: Row(
          children: [
            // Wishlist Button
            Container(
              height: 52,
              width: 52,
              margin: const EdgeInsets.only(right: 10),
              decoration: BoxDecoration(
                border: Border.all(color: AppColors.divider, width: 1.2),
                borderRadius: BorderRadius.circular(14),
                color: AppColors.surface,
              ),
              child: Center(
                child: WishlistButton(
                  productId: product.id,
                  initialLiked: product.liked,
                  size: 22,
                  backgroundColor: Colors.transparent,
                ),
              ),
            ),
            // Add to Cart Button (Outlined)
            Expanded(
              flex: 1,
              child: SizedBox(
                height: 52,
                child: OutlinedButton.icon(
                  onPressed: inStock && !_isAddingToCart
                      ? () => _handleAddToCart(product)
                      : null,
                  icon: _isAddingToCart
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: AppColors.primary,
                          ),
                        )
                      : const Icon(Icons.add_shopping_cart_rounded, size: 20),
                  label: const Text('Add to Cart'),
                  style: OutlinedButton.styleFrom(
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                  ),
                ),
              ),
            ),
            const SizedBox(width: 12),
            // Buy Now Button (Gradient)
            Expanded(
              flex: 1,
              child: GradientButton(
                label: 'Buy Now',
                isLoading: false,
                onPressed: inStock
                    ? () async {
                        final auth = ref.read(authProvider);
                        if (auth.status != AuthStatus.authenticated) {
                          context.push('/login');
                          return;
                        }
                        setState(() => _isAddingToCart = true);
                        final success = await ref
                            .read(cartProvider.notifier)
                            .addItem(
                              product.id,
                              _quantity,
                              variantId: _selectedVariant?.id,
                            );
                        if (!mounted) return;
                        setState(() => _isAddingToCart = false);
                        if (success && context.mounted) {
                          context.push('/checkout');
                        }
                      }
                    : null,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _handleAddToCart(Product product) async {
    final auth = ref.read(authProvider);
    if (auth.status != AuthStatus.authenticated) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: const Text('Please sign in to add items to cart'),
          action: SnackBarAction(
            label: 'Sign In',
            textColor: Colors.white,
            onPressed: () => context.push('/login'),
          ),
          backgroundColor: AppColors.primary,
          behavior: SnackBarBehavior.floating,
        ),
      );
      return;
    }

    setState(() => _isAddingToCart = true);
    final success = await ref
        .read(cartProvider.notifier)
        .addItem(product.id, _quantity, variantId: _selectedVariant?.id);

    if (!mounted) return;
    setState(() => _isAddingToCart = false);

    if (!context.mounted) return;
    final messenger = ScaffoldMessenger.of(context);
    messenger
      ..hideCurrentSnackBar()
      ..clearSnackBars();

    if (success) {
      messenger.showSnackBar(
        SnackBar(
          content: Text('Added ${_quantity}x "${product.name}" to cart'),
          action: SnackBarAction(
            label: 'View Cart',
            textColor: Colors.white,
            onPressed: () {
              messenger.hideCurrentSnackBar();
              if (context.mounted) context.push('/cart');
            },
          ),
          backgroundColor: AppColors.success,
          behavior: SnackBarBehavior.floating,
          duration: const Duration(seconds: 4),
        ),
      );
    } else {
      messenger.showSnackBar(
        const SnackBar(
          content: Text('Failed to add item to cart. Please try again.'),
          backgroundColor: AppColors.error,
          behavior: SnackBarBehavior.floating,
          duration: Duration(seconds: 3),
        ),
      );
    }
  }

  Widget _buildCartIcon(int count) {
    return GestureDetector(
      onTap: () => context.push('/cart'),
      child: Container(
        padding: const EdgeInsets.all(8),
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            const Icon(
              Icons.shopping_bag_outlined,
              size: 24,
              color: AppColors.textPrimary,
            ),
            if (count > 0)
              Positioned(
                right: -4,
                top: -4,
                child: Container(
                  width: 16,
                  height: 16,
                  decoration: const BoxDecoration(
                    color: AppColors.primary,
                    shape: BoxShape.circle,
                  ),
                  child: Center(
                    child: Text(
                      count > 9 ? '9+' : '$count',
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 9,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildWishlistBadge(Product product) {
    final wishlistMap = ref.watch(wishlistProvider);
    final isLiked = wishlistMap.containsKey(product.id)
        ? wishlistMap[product.id]!
        : product.liked;
    final countDelta = isLiked == product.liked ? 0 : (isLiked ? 1 : -1);
    final currentCount = (product.likeCount + countDelta).clamp(0, 999999);

    return InkWell(
      onTap: () async {
        final auth = ref.read(authProvider);
        if (auth.status != AuthStatus.authenticated) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: const Text('Please sign in to add to wishlist'),
              action: SnackBarAction(
                label: 'Sign In',
                textColor: Colors.white,
                onPressed: () => context.push('/login'),
              ),
              behavior: SnackBarBehavior.floating,
              duration: const Duration(seconds: 3),
            ),
          );
          return;
        }
        await ref.read(wishlistProvider.notifier).toggle(product.id);
      },
      borderRadius: BorderRadius.circular(20),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: isLiked
              ? Colors.redAccent.withValues(alpha: 0.08)
              : AppColors.surfaceVariant.withValues(alpha: 0.6),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: isLiked
                ? Colors.redAccent.withValues(alpha: 0.3)
                : AppColors.divider,
            width: 1,
          ),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              isLiked ? Icons.favorite_rounded : Icons.favorite_border_rounded,
              size: 16,
              color: isLiked ? Colors.redAccent : AppColors.textSecondary,
            ),
            const SizedBox(width: 6),
            Text(
              isLiked ? 'Saved to Wishlist' : 'Add to Wishlist',
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: isLiked ? Colors.redAccent : AppColors.textSecondary,
              ),
            ),
            if (currentCount > 0) ...[
              const SizedBox(width: 6),
              Container(
                width: 3,
                height: 3,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: isLiked ? Colors.redAccent : AppColors.textHint,
                ),
              ),
              const SizedBox(width: 6),
              Text(
                '$currentCount',
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                  color: isLiked ? Colors.redAccent : AppColors.textSecondary,
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

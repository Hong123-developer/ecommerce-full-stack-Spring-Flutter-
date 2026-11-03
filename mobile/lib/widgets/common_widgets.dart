import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:go_router/go_router.dart';
import 'package:shimmer/shimmer.dart';

import '../config/theme.dart';
import '../providers/providers.dart';

// ──────────────────────────────────────────────
// Wishlist Heart Button
// ──────────────────────────────────────────────
class WishlistButton extends ConsumerWidget {
  final int productId;
  final bool initialLiked;
  final double size;
  final Color? activeColor;
  final Color? inactiveColor;
  final Color? backgroundColor;
  final VoidCallback? onToggled;

  const WishlistButton({
    super.key,
    required this.productId,
    this.initialLiked = false,
    this.size = 18,
    this.activeColor,
    this.inactiveColor,
    this.backgroundColor,
    this.onToggled,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final wishlistMap = ref.watch(wishlistProvider);
    final isLiked = wishlistMap.containsKey(productId)
        ? wishlistMap[productId]!
        : initialLiked;

    return Material(
      color: Colors.transparent,
      child: InkWell(
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
          await ref.read(wishlistProvider.notifier).toggle(productId);
          onToggled?.call();
        },
        borderRadius: BorderRadius.circular(20),
        child: Container(
          padding: const EdgeInsets.all(6),
          decoration: BoxDecoration(
            color: backgroundColor ?? Colors.white.withValues(alpha: 0.92),
            shape: BoxShape.circle,
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.08),
                blurRadius: 6,
                offset: const Offset(0, 2),
              ),
            ],
          ),
          child: AnimatedSwitcher(
            duration: const Duration(milliseconds: 200),
            transitionBuilder: (child, anim) =>
                ScaleTransition(scale: anim, child: child),
            child: Icon(
              isLiked ? Icons.favorite_rounded : Icons.favorite_border_rounded,
              key: ValueKey<bool>(isLiked),
              size: size,
              color: isLiked
                  ? (activeColor ?? Colors.redAccent)
                  : (inactiveColor ?? AppColors.textSecondary),
            ),
          ),
        ),
      ),
    );
  }
}

// ──────────────────────────────────────────────
// Product Card
// ──────────────────────────────────────────────
class ProductCard extends StatelessWidget {
  final int? productId;
  final String? imageUrl;
  final String name;
  final double price;
  final double? originalPrice;
  final int discountPercent;
  final double rating;
  final int reviewCount;
  final bool inStock;
  final bool isLiked;
  final int likeCount;
  final VoidCallback? onTap;

  const ProductCard({
    super.key,
    this.productId,
    this.imageUrl,
    required this.name,
    required this.price,
    this.originalPrice,
    this.discountPercent = 0,
    this.rating = 0,
    this.reviewCount = 0,
    this.inStock = true,
    this.isLiked = false,
    this.likeCount = 0,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.04),
              blurRadius: 12,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Image
            Expanded(
              flex: 3,
              child: Stack(
                children: [
                  ClipRRect(
                    borderRadius: const BorderRadius.vertical(
                      top: Radius.circular(16),
                    ),
                    child: Container(
                      color: Colors.white,
                      width: double.infinity,
                      height: double.infinity,
                      padding: const EdgeInsets.all(10),
                      child: imageUrl != null
                          ? CachedNetworkImage(
                              imageUrl: imageUrl!,
                              fit: BoxFit.contain,
                              placeholder: (_, __) => _shimmerBox(),
                              errorWidget: (_, __, ___) => _placeholderImage(),
                            )
                          : _placeholderImage(),
                    ),
                  ),
                  if (productId != null)
                    Positioned(
                      top: 8,
                      right: 8,
                      child: WishlistButton(
                        productId: productId!,
                        initialLiked: isLiked,
                        size: 17,
                      ),
                    ),
                  if (!inStock)
                    Positioned.fill(
                      child: Container(
                        decoration: BoxDecoration(
                          color: Colors.black.withValues(alpha: 0.5),
                          borderRadius: const BorderRadius.vertical(
                            top: Radius.circular(16),
                          ),
                        ),
                        alignment: Alignment.center,
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 12,
                            vertical: 6,
                          ),
                          decoration: BoxDecoration(
                            color: AppColors.error,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: const Text(
                            'Out of Stock',
                            style: TextStyle(
                              color: Colors.white,
                              fontSize: 11,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ),
                    ),
                ],
              ),
            ),
            // Info
            Expanded(
              flex: 2,
              child: Padding(
                padding: const EdgeInsets.all(10),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      name,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w500,
                        color: AppColors.textPrimary,
                        height: 1.3,
                      ),
                    ),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        if (rating > 0)
                          Row(
                            children: [
                              ...List.generate(5, (i) {
                                return Icon(
                                  i < rating.round()
                                      ? Icons.star_rounded
                                      : Icons.star_outline_rounded,
                                  size: 13,
                                  color: i < rating.round()
                                      ? AppColors.starFilled
                                      : AppColors.starEmpty,
                                );
                              }),
                              const SizedBox(width: 4),
                              Text(
                                '($reviewCount)',
                                style: const TextStyle(
                                  fontSize: 11,
                                  color: AppColors.textSecondary,
                                ),
                              ),
                            ],
                          ),
                        const SizedBox(height: 4),
                        Row(
                          children: [
                            Text(
                              '\$${price.toStringAsFixed(2)}',
                              style: const TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.w700,
                                color: AppColors.primary,
                              ),
                            ),
                            if (discountPercent > 0 &&
                                originalPrice != null &&
                                originalPrice! > price) ...[
                              const SizedBox(width: 6),
                              Text(
                                '\$${originalPrice!.toStringAsFixed(2)}',
                                style: const TextStyle(
                                  fontSize: 11,
                                  color: AppColors.textSecondary,
                                  decoration: TextDecoration.lineThrough,
                                ),
                              ),
                              const SizedBox(width: 4),
                              Text(
                                '-$discountPercent%',
                                style: const TextStyle(
                                  fontSize: 11,
                                  fontWeight: FontWeight.w700,
                                  color: AppColors.accent,
                                ),
                              ),
                            ],
                          ],
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _shimmerBox() {
    return Shimmer.fromColors(
      baseColor: AppColors.shimmerBase,
      highlightColor: AppColors.shimmerHighlight,
      child: Container(color: Colors.white),
    );
  }

  Widget _placeholderImage() {
    return Container(
      color: AppColors.surfaceVariant,
      child: const Center(
        child: Icon(Icons.image_rounded, size: 40, color: AppColors.textHint),
      ),
    );
  }
}

// ──────────────────────────────────────────────
// Category Chip
// ──────────────────────────────────────────────
class CategoryChip extends StatelessWidget {
  final String label;
  final String? imageUrl;
  final bool isSelected;
  final VoidCallback? onTap;

  const CategoryChip({
    super.key,
    required this.label,
    this.imageUrl,
    this.isSelected = false,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final hasImage = imageUrl != null && imageUrl!.isNotEmpty;
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: EdgeInsets.only(
          left: hasImage ? 6 : 16,
          right: 16,
          top: hasImage ? 5 : 10,
          bottom: hasImage ? 5 : 10,
        ),
        decoration: BoxDecoration(
          color: isSelected ? AppColors.primary : AppColors.surfaceVariant,
          borderRadius: BorderRadius.circular(12),
          border: isSelected ? null : Border.all(color: AppColors.divider),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (hasImage) ...[
              ClipOval(
                child: SizedBox(
                  width: 28,
                  height: 28,
                  child: CachedNetworkImage(
                    imageUrl: imageUrl!,
                    fit: BoxFit.cover,
                    placeholder: (_, __) => Container(
                      color: isSelected
                          ? Colors.white.withValues(alpha: 0.25)
                          : AppColors.divider,
                    ),
                    errorWidget: (_, __, ___) => Icon(
                      Icons.category_outlined,
                      size: 16,
                      color: isSelected ? Colors.white70 : AppColors.textHint,
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 8),
            ],
            Text(
              label,
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: isSelected ? Colors.white : AppColors.textSecondary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ──────────────────────────────────────────────
// Order Status Badge
// ──────────────────────────────────────────────
class OrderStatusBadge extends StatelessWidget {
  final String status;

  const OrderStatusBadge({super.key, required this.status});

  @override
  Widget build(BuildContext context) {
    Color color;
    IconData icon;
    switch (status.toUpperCase()) {
      case 'PENDING':
        color = AppColors.pending;
        icon = Icons.access_time_rounded;
        break;
      case 'CONFIRMED':
        color = AppColors.confirmed;
        icon = Icons.check_circle_outline_rounded;
        break;
      case 'PROCESSING':
        color = AppColors.processing;
        icon = Icons.settings_rounded;
        break;
      case 'SHIPPED':
        color = AppColors.shipped;
        icon = Icons.local_shipping_rounded;
        break;
      case 'DELIVERED':
        color = AppColors.delivered;
        icon = Icons.done_all_rounded;
        break;
      case 'CANCELLED':
      case 'REFUNDED':
        color = AppColors.cancelled;
        icon = Icons.cancel_rounded;
        break;
      default:
        color = AppColors.textSecondary;
        icon = Icons.info_outline_rounded;
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: color),
          const SizedBox(width: 4),
          Text(
            status,
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w600,
              color: color,
            ),
          ),
        ],
      ),
    );
  }
}

// ──────────────────────────────────────────────
// Loading Shimmer
// ──────────────────────────────────────────────
class ShimmerLoading extends StatelessWidget {
  final double width;
  final double height;
  final double borderRadius;

  const ShimmerLoading({
    super.key,
    this.width = double.infinity,
    required this.height,
    this.borderRadius = 12,
  });

  @override
  Widget build(BuildContext context) {
    return Shimmer.fromColors(
      baseColor: AppColors.shimmerBase,
      highlightColor: AppColors.shimmerHighlight,
      child: Container(
        width: width,
        height: height,
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(borderRadius),
        ),
      ),
    );
  }
}

// ──────────────────────────────────────────────
// Empty State Widget
// ──────────────────────────────────────────────
class EmptyState extends StatelessWidget {
  final IconData icon;
  final String title;
  final String? subtitle;
  final String? actionLabel;
  final VoidCallback? onAction;

  const EmptyState({
    super.key,
    required this.icon,
    required this.title,
    this.subtitle,
    this.actionLabel,
    this.onAction,
  });

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                color: AppColors.primary.withValues(alpha: 0.08),
                shape: BoxShape.circle,
              ),
              child: Icon(icon, size: 48, color: AppColors.primary),
            ),
            const SizedBox(height: 24),
            Text(
              title,
              textAlign: TextAlign.center,
              style: const TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w600,
                color: AppColors.textPrimary,
              ),
            ),
            if (subtitle != null) ...[
              const SizedBox(height: 8),
              Text(
                subtitle!,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  fontSize: 14,
                  color: AppColors.textSecondary,
                ),
              ),
            ],
            if (actionLabel != null) ...[
              const SizedBox(height: 24),
              ElevatedButton(onPressed: onAction, child: Text(actionLabel!)),
            ],
          ],
        ),
      ),
    );
  }
}

// ──────────────────────────────────────────────
// Gradient Button
// ──────────────────────────────────────────────
class GradientButton extends StatelessWidget {
  final String label;
  final VoidCallback? onPressed;
  final bool isLoading;
  final LinearGradient gradient;
  final double? width;

  const GradientButton({
    super.key,
    required this.label,
    this.onPressed,
    this.isLoading = false,
    this.gradient = AppColors.primaryGradient,
    this.width,
  });

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: width ?? double.infinity,
      height: 52,
      child: DecoratedBox(
        decoration: BoxDecoration(
          gradient: onPressed != null ? gradient : null,
          color: onPressed == null ? AppColors.textHint : null,
          borderRadius: BorderRadius.circular(14),
          boxShadow: onPressed != null
              ? [
                  BoxShadow(
                    color: AppColors.primary.withValues(alpha: 0.3),
                    blurRadius: 12,
                    offset: const Offset(0, 4),
                  ),
                ]
              : null,
        ),
        child: MaterialButton(
          onPressed: isLoading ? null : onPressed,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
          child: isLoading
              ? const SizedBox(
                  width: 22,
                  height: 22,
                  child: CircularProgressIndicator(
                    strokeWidth: 2.5,
                    color: Colors.white,
                  ),
                )
              : Text(
                  label,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                  ),
                ),
        ),
      ),
    );
  }
}

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../config/theme.dart';
import '../../providers/providers.dart';
import '../../widgets/common_widgets.dart';

class WishlistScreen extends ConsumerWidget {
  const WishlistScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final wishlistAsync = ref.watch(wishlistPageProvider);
    final wishlistMap = ref.watch(wishlistProvider);
    final language = ref.watch(appLanguageProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        leading: Navigator.of(context).canPop()
            ? IconButton(
                icon: const Icon(Icons.arrow_back_ios_new_rounded),
                onPressed: () => context.pop(),
              )
            : null,
        title: Text(appText(language, 'wishlist')),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded),
            onPressed: () => ref.refresh(wishlistPageProvider),
          ),
        ],
      ),
      body: wishlistAsync.when(
        data: (products) {
          // Filter out items unliked in local state
          final activeItems = products.where((p) {
            final locallyLiked = wishlistMap[p.id];
            return locallyLiked ?? p.liked;
          }).toList();

          if (activeItems.isEmpty) {
            return Center(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 32),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      width: 96,
                      height: 96,
                      decoration: BoxDecoration(
                        color: Colors.redAccent.withValues(alpha: 0.1),
                        shape: BoxShape.circle,
                      ),
                      child: const Center(
                        child: Icon(
                          Icons.favorite_border_rounded,
                          size: 48,
                          color: Colors.redAccent,
                        ),
                      ),
                    ),
                    const SizedBox(height: 24),
                    Text(
                      appText(language, 'wishlistEmpty'),
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w700,
                        color: AppColors.textPrimary,
                      ),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 10),
                    Text(
                      appText(language, 'wishlistEmptyDescription'),
                      style: TextStyle(
                        fontSize: 14,
                        color: AppColors.textSecondary,
                        height: 1.4,
                      ),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 28),
                    SizedBox(
                      width: 200,
                      height: 48,
                      child: ElevatedButton.icon(
                        onPressed: () => context.go('/shop'),
                        icon: const Icon(Icons.shopping_bag_outlined, size: 18),
                        label: Text(appText(language, 'exploreShop')),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.primary,
                          foregroundColor: Colors.white,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(14),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            );
          }

          return RefreshIndicator(
            color: AppColors.primary,
            onRefresh: () async => ref.refresh(wishlistPageProvider),
            child: GridView.builder(
              padding: const EdgeInsets.all(16),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 2,
                childAspectRatio: 0.65,
                crossAxisSpacing: 12,
                mainAxisSpacing: 12,
              ),
              itemCount: activeItems.length,
              itemBuilder: (context, index) {
                final product = activeItems[index];
                return ProductCard(
                  productId: product.id,
                  imageUrl: product.imageUrl,
                  name: product.name,
                  price: product.price,
                  rating: product.rating,
                  reviewCount: product.reviewCount,
                  inStock: product.inStock,
                  isLiked: true,
                  likeCount: product.likeCount,
                  onTap: () => context.push('/products/${product.id}'),
                );
              },
            ),
          );
        },
        loading: () => const Center(
          child: CircularProgressIndicator(color: AppColors.primary),
        ),
        error: (err, _) => Center(
          child: EmptyState(
            icon: Icons.error_outline_rounded,
            title: appText(language, 'failedLoadWishlist'),
            subtitle: err.toString(),
            actionLabel: appText(language, 'retry'),
            onAction: () => ref.refresh(wishlistPageProvider),
          ),
        ),
      ),
    );
  }
}

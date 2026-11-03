import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../config/theme.dart';
import '../../providers/providers.dart';
import '../../widgets/app_drawer.dart';
import '../../widgets/common_widgets.dart';

class ProductsScreen extends ConsumerStatefulWidget {
  const ProductsScreen({super.key});

  @override
  ConsumerState<ProductsScreen> createState() => _ProductsScreenState();
}

class _ProductsScreenState extends ConsumerState<ProductsScreen> {
  final _searchCtrl = TextEditingController();
  final _scrollCtrl = ScrollController();

  @override
  void initState() {
    super.initState();
    _searchCtrl.text = ref.read(productFilterProvider).search ?? '';
    _scrollCtrl.addListener(_onScroll);
  }

  void _onScroll() {
    if (_scrollCtrl.hasClients) {
      final maxScroll = _scrollCtrl.position.maxScrollExtent;
      final currentScroll = _scrollCtrl.position.pixels;
      if (currentScroll >= maxScroll - 300) {
        ref.read(paginatedProductsProvider.notifier).loadMore();
      }
    }
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    _scrollCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    ref.listen(productFilterProvider, (_, next) {
      final query = next.search ?? '';
      if (_searchCtrl.text != query) _searchCtrl.text = query;
    });
    final filter = ref.watch(productFilterProvider);
    final paginated = ref.watch(paginatedProductsProvider);
    final categories = ref.watch(categoriesProvider);
    final language = ref.watch(appLanguageProvider);

    return Scaffold(
      drawer: const AppDrawer(),
      body: SafeArea(
        child: Column(
          children: [
            // ─── Header with drawer ───
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 12, 20, 0),
              child: Row(
                children: [
                  Builder(
                    builder: (drawerContext) => IconButton(
                      icon: const Icon(Icons.menu_rounded),
                      onPressed: () => Scaffold.of(drawerContext).openDrawer(),
                    ),
                  ),
                  const SizedBox(width: 4),
                  Text(
                    appText(language, 'shop'),
                    style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
                  ),
                ],
              ),
            ),

            // ─── Search Bar ───
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 8, 20, 8),
              child: TextField(
                controller: _searchCtrl,
                onSubmitted: (val) {
                  ref.read(productFilterProvider.notifier).state = filter
                      .copyWith(search: val, page: 0);
                },
                decoration: InputDecoration(
                  hintText: appText(language, 'searchProducts'),
                  prefixIcon: const Icon(Icons.search_rounded, size: 22),
                  suffixIcon: _searchCtrl.text.isNotEmpty
                      ? IconButton(
                          icon: const Icon(Icons.close_rounded, size: 20),
                          onPressed: () {
                            _searchCtrl.clear();
                            ref.read(productFilterProvider.notifier).state =
                                filter.copyWith(clearSearch: true, page: 0);
                          },
                        )
                      : null,
                ),
              ),
            ),

            // ─── Category Filter ───
            categories.when(
              data: (cats) => SizedBox(
                height: 48,
                child: ListView(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  children: [
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                      child: CategoryChip(
                        label: appText(language, 'all'),
                        isSelected: filter.categoryId == null,
                        onTap: () {
                          ref.read(productFilterProvider.notifier).state =
                              filter.copyWith(clearCategory: true, page: 0);
                        },
                      ),
                    ),
                    ...cats.map(
                      (cat) => Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 4),
                        child: CategoryChip(
                          label: cat.name,
                          imageUrl: cat.imageUrl,
                          isSelected: filter.categoryId == cat.id,
                          onTap: () {
                            ref.read(productFilterProvider.notifier).state =
                                filter.copyWith(categoryId: cat.id, page: 0);
                          },
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              loading: () => const SizedBox(height: 44),
              error: (_, __) => const SizedBox(height: 44),
            ),

            const SizedBox(height: 8),

            // ─── Product Grid (Infinite Scroll) ───
            Expanded(
              child: Builder(
                builder: (context) {
                  // Initial loading
                  if (paginated.isLoading && paginated.products.isEmpty) {
                    return GridView.builder(
                      padding: const EdgeInsets.all(16),
                      gridDelegate:
                          const SliverGridDelegateWithFixedCrossAxisCount(
                            crossAxisCount: 2,
                            childAspectRatio: 0.65,
                            crossAxisSpacing: 12,
                            mainAxisSpacing: 12,
                          ),
                      itemCount: 6,
                      itemBuilder: (_, __) =>
                          const ShimmerLoading(height: 240, borderRadius: 16),
                    );
                  }

                  // Error state
                  if (paginated.error != null && paginated.products.isEmpty) {
                    return Center(
                      child: EmptyState(
                        icon: Icons.error_outline_rounded,
                        title: appText(language, 'error'),
                        subtitle: paginated.error!,
                        actionLabel: appText(language, 'retry'),
                        onAction: () => ref
                            .read(paginatedProductsProvider.notifier)
                            .fetchFirstPage(),
                      ),
                    );
                  }

                  // Empty state
                  if (paginated.products.isEmpty) {
                    return EmptyState(
                      icon: Icons.search_off_rounded,
                      title: appText(language, 'noProducts'),
                      subtitle: appText(language, 'tryDifferentSearch'),
                    );
                  }

                  final items = paginated.products;

                  return RefreshIndicator(
                    color: AppColors.primary,
                    onRefresh: () => ref
                        .read(paginatedProductsProvider.notifier)
                        .fetchFirstPage(),
                    child: CustomScrollView(
                      controller: _scrollCtrl,
                      physics: const AlwaysScrollableScrollPhysics(),
                      slivers: [
                        // Results count
                        SliverToBoxAdapter(
                          child: Padding(
                            padding: const EdgeInsets.fromLTRB(20, 4, 20, 8),
                            child: Row(
                              children: [
                                Text(
                                  'Showing ${items.length} of ${paginated.totalElements} products',
                                  style: const TextStyle(
                                    fontSize: 13,
                                    fontWeight: FontWeight.w600,
                                    color: AppColors.textSecondary,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),

                        // 2-column Grid of products
                        SliverPadding(
                          padding: const EdgeInsets.symmetric(horizontal: 16),
                          sliver: SliverGrid(
                            gridDelegate:
                                const SliverGridDelegateWithFixedCrossAxisCount(
                                  crossAxisCount: 2,
                                  childAspectRatio: 0.65,
                                  crossAxisSpacing: 12,
                                  mainAxisSpacing: 12,
                                ),
                            delegate: SliverChildBuilderDelegate((
                              context,
                              index,
                            ) {
                              final product = items[index];
                              return ProductCard(
                                productId: product.id,
                                imageUrl: product.imageUrl,
                                name: product.name,
                                price: product.price,
                                originalPrice: product.originalPrice,
                                discountPercent: product.discountPercent,
                                rating: product.rating,
                                reviewCount: product.reviewCount,
                                inStock: product.inStock,
                                isLiked: product.liked,
                                likeCount: product.likeCount,
                                onTap: () =>
                                    context.push('/products/${product.id}'),
                              );
                            }, childCount: items.length),
                          ),
                        ),

                        // Bottom spinner when scrolling & loading more items
                        if (paginated.isLoadingMore)
                          const SliverToBoxAdapter(
                            child: Padding(
                              padding: EdgeInsets.symmetric(vertical: 24),
                              child: Center(
                                child: CircularProgressIndicator(
                                  color: AppColors.primary,
                                  strokeWidth: 2.5,
                                ),
                              ),
                            ),
                          )
                        else if (paginated.hasMore)
                          SliverToBoxAdapter(
                            child: Padding(
                              padding: const EdgeInsets.symmetric(vertical: 16),
                              child: Center(
                                child: TextButton.icon(
                                  onPressed: () => ref
                                      .read(paginatedProductsProvider.notifier)
                                      .loadMore(),
                                  icon: const Icon(
                                    Icons.keyboard_arrow_down_rounded,
                                    size: 20,
                                  ),
                                  label: const Text('Load More Products'),
                                  style: TextButton.styleFrom(
                                    foregroundColor: AppColors.primary,
                                    textStyle: const TextStyle(
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          )
                        else
                          SliverToBoxAdapter(
                            child: Padding(
                              padding: const EdgeInsets.symmetric(vertical: 20),
                              child: Center(
                                child: Text(
                                  'Showing all ${paginated.totalElements} products',
                                  style: const TextStyle(
                                    fontSize: 12,
                                    fontWeight: FontWeight.w500,
                                    color: AppColors.textHint,
                                  ),
                                ),
                              ),
                            ),
                          ),
                      ],
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}

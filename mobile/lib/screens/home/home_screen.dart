import 'dart:async';

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../config/api_config.dart';
import '../../config/theme.dart';
import '../../models/models.dart';
import '../../providers/providers.dart';
import '../../widgets/app_drawer.dart';
import '../../widgets/common_widgets.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  final _searchCtrl = TextEditingController();
  final _homeScrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    Future.microtask(() {
      if (!mounted) return;
      ref.read(productFilterProvider.notifier).state = const ProductFilter();
    });
    _homeScrollController.addListener(_loadMorePopularProducts);
    // Fetch cart when user is authenticated
    final auth = ref.read(authProvider);
    if (auth.status == AuthStatus.authenticated) {
      Future.microtask(() => ref.read(cartProvider.notifier).fetchCart());
    }
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    _homeScrollController.dispose();
    super.dispose();
  }

  void _loadMorePopularProducts() {
    if (!_homeScrollController.hasClients) return;
    final position = _homeScrollController.position;
    if (position.pixels >= position.maxScrollExtent - 500) {
      ref.read(paginatedProductsProvider.notifier).loadMore();
    }
  }

  bool get _isAuth => ref.read(authProvider).status == AuthStatus.authenticated;

  Future<void> _refresh() async {
    ref.invalidate(heroSlidesProvider);
    ref.invalidate(categoriesProvider);
    ref.invalidate(homeProductsProvider);
    if (_isAuth) {
      ref.invalidate(addressesProvider);
      ref.invalidate(unreadCountProvider);
      unawaited(ref.read(cartProvider.notifier).fetchCart());
    }
    try {
      await ref.read(homeProductsProvider.future);
    } catch (_) {}
  }

  // ─── Navigation helpers ───

  /// Opens the Shop tab with [filter] applied (no argument = everything, newest first).
  void _openShop([ProductFilter filter = const ProductFilter()]) {
    ref.read(productFilterProvider.notifier).state = filter;
    context.go('/shop');
  }

  void _search(String query) {
    final q = query.trim();
    if (q.isEmpty) return;
    FocusScope.of(context).unfocus();
    _searchCtrl.clear();
    _openShop(ProductFilter(search: q));
  }

  /// Hero links are written for the web store: '/products/12', '/products?category=3', 'https://...'.
  Future<void> _openLink(String? link) async {
    final value = link?.trim() ?? '';
    if (value.startsWith('http://') || value.startsWith('https://')) {
      try {
        await launchUrl(Uri.parse(value), mode: LaunchMode.externalApplication);
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context)
              .showSnackBar(SnackBar(content: Text('Could not open link: $e')));
        }
      }
      return;
    }
    final uri = Uri.tryParse(value);
    final segments = uri?.pathSegments ?? const <String>[];
    if (segments.length == 2 &&
        segments[0] == 'products' &&
        int.tryParse(segments[1]) != null) {
      if (mounted) context.push('/products/${segments[1]}');
      return;
    }
    final params = uri?.queryParameters ?? const <String, String>{};
    final categoryId = int.tryParse(
      params['category'] ?? params['categoryId'] ?? '',
    );
    final search = params['search'] ?? params['q'];
    _openShop(ProductFilter(categoryId: categoryId, search: search));
  }

  @override
  Widget build(BuildContext context) {
    final isAuth =
        ref.watch(authProvider.select((s) => s.status)) ==
        AuthStatus.authenticated;
    final user = ref.watch(authProvider).user;
    final heroSlides = ref.watch(heroSlidesProvider);
    final categories = ref.watch(categoriesProvider);
    final products = ref.watch(homeProductsProvider);
    final cartCount = ref.watch(cartProvider.select((c) => c.totalQuantity));
    final palette = context.palette;

    return Scaffold(
      backgroundColor: palette.surface,
      drawer: const AppDrawer(),
      body: SafeArea(
        child: RefreshIndicator(
          color: palette.primary,
          onRefresh: _refresh,
          child: CustomScrollView(
            controller: _homeScrollController,
            physics: const AlwaysScrollableScrollPhysics(),
            slivers: [
              // ─── Header: profile, notifications, cart ───
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(18, 14, 16, 0),
                  child: Row(
                    children: [
                      Builder(
                        builder: (drawerContext) => GestureDetector(
                          onTap: () => Scaffold.of(drawerContext).openDrawer(),
                          child: Container(
                            width: 42,
                            height: 42,
                            decoration: BoxDecoration(
                              color: palette.surface,
                              borderRadius: BorderRadius.circular(14),
                              border: Border.all(color: palette.divider),
                              boxShadow: [
                                BoxShadow(
                                  color: palette.primary.withValues(
                                    alpha: 0.08,
                                  ),
                                  blurRadius: 10,
                                  offset: const Offset(0, 4),
                                ),
                              ],
                            ),
                            child: Center(
                              child: Icon(
                                Icons.menu_rounded,
                                color: palette.primary,
                                size: 22,
                              ),
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 10),
                      _buildProfileAvatar(isAuth),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              isAuth && user?.fullName.isNotEmpty == true
                                  ? user!.fullName
                                  : 'ShopOnline',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                color: palette.textPrimary,
                                fontSize: 16,
                                fontWeight: FontWeight.w800,
                                letterSpacing: -0.3,
                              ),
                            ),
                          ],
                        ),
                      ),
                      if (isAuth) ...[
                        _BadgeIconButton(
                          icon: Icons.notifications_none_rounded,
                          count: ref.watch(unreadCountProvider).value ?? 0,
                          onTap: () => context.push('/notifications'),
                        ),
                      ],
                      _BadgeIconButton(
                        icon: Icons.shopping_cart_outlined,
                        count: cartCount,
                        onTap: () => context.go('/cart'),
                      ),
                    ],
                  ),
                ),
              ),

              // ─── Search ───
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 18, 16, 4),
                  child: TextField(
                    controller: _searchCtrl,
                    textInputAction: TextInputAction.search,
                    onSubmitted: _search,
                    style: const TextStyle(fontSize: 14),
                    decoration: InputDecoration(
                      hintText: 'Search for products, brands and more...',
                      hintStyle: TextStyle(
                        fontSize: 13.5,
                        color: palette.textHint,
                      ),
                      prefixIcon: Icon(
                        Icons.search_rounded,
                        size: 22,
                        color: palette.textSecondary,
                      ),
                      suffixIcon: Padding(
                        padding: const EdgeInsets.only(right: 8),
                        child: Icon(
                          Icons.tune_rounded,
                          size: 20,
                          color: palette.primary.withValues(alpha: 0.8),
                        ),
                      ),
                      filled: true,
                      fillColor: palette.surface,
                      contentPadding: const EdgeInsets.symmetric(vertical: 14),
                      border: _pillBorder(),
                      enabledBorder: _pillBorder(),
                      focusedBorder: _pillBorder(palette.primary),
                    ),
                  ),
                ),
              ),

              // ─── Hero carousel ───
              heroSlides.when(
                data: (slides) => SliverToBoxAdapter(
                  child: slides.isEmpty
                      ? const SizedBox.shrink()
                      : Padding(
                          padding: const EdgeInsets.only(top: 12),
                          child: _HeroCarousel(
                            slides: slides,
                            onOpen: _openLink,
                          ),
                        ),
                ),
                loading: () => const SliverToBoxAdapter(
                  child: Padding(
                    padding: EdgeInsets.fromLTRB(16, 12, 16, 0),
                    child: ShimmerLoading(height: 176, borderRadius: 18),
                  ),
                ),
                error: (_, _) =>
                    const SliverToBoxAdapter(child: SizedBox.shrink()),
              ),

              // ─── Special For You ───
              SliverToBoxAdapter(
                child: _SectionHeader(
                  title: 'Picked for you',
                  onSeeAll: _openShop,
                ),
              ),
              SliverToBoxAdapter(
                child: SizedBox(
                  height:
                      _ProductTile.imageHeightSmall + _ProductTile.infoHeight,
                  child: products.when(
                    data: (list) => list.isEmpty
                        ? Center(
                            child: Text(
                              'No products yet',
                              style: TextStyle(color: palette.textSecondary),
                            ),
                          )
                        : ListView.separated(
                            scrollDirection: Axis.horizontal,
                            padding: const EdgeInsets.symmetric(horizontal: 16),
                            itemCount: list.length > 10 ? 10 : list.length,
                            separatorBuilder: (_, _) =>
                                const SizedBox(width: 10),
                            itemBuilder: (context, i) => _ProductTile(
                              product: list[i],
                              width: 128,
                              imageHeight: _ProductTile.imageHeightSmall,
                              onTap: () =>
                                  context.push('/products/${list[i].id}'),
                            ),
                          ),
                    loading: () => ListView.separated(
                      scrollDirection: Axis.horizontal,
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      itemCount: 4,
                      separatorBuilder: (_, _) => const SizedBox(width: 10),
                      itemBuilder: (_, _) => const ShimmerLoading(
                        width: 128,
                        height: 188,
                        borderRadius: 14,
                      ),
                    ),
                    error: (e, _) => Center(
                      child: TextButton.icon(
                        onPressed: () => ref.invalidate(homeProductsProvider),
                        icon: const Icon(Icons.refresh_rounded, size: 18),
                        label: const Text('Could not load products. Retry'),
                      ),
                    ),
                  ),
                ),
              ),

              // ─── Top Categories ───
              SliverToBoxAdapter(
                child: _SectionHeader(
                  title: 'Top Categories',
                  onSeeAll: _openShop,
                ),
              ),
              SliverToBoxAdapter(
                child: SizedBox(
                  height: 90,
                  child: categories.when(
                    data: (cats) => ListView.separated(
                      scrollDirection: Axis.horizontal,
                      padding: const EdgeInsets.symmetric(horizontal: 12),
                      itemCount: cats.length,
                      separatorBuilder: (_, _) => const SizedBox(width: 4),
                      itemBuilder: (context, i) => _CategoryItem(
                        category: cats[i],
                        onTap: () =>
                            _openShop(ProductFilter(categoryId: cats[i].id)),
                      ),
                    ),
                    loading: () => ListView.separated(
                      scrollDirection: Axis.horizontal,
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      itemCount: 5,
                      separatorBuilder: (_, _) => const SizedBox(width: 14),
                      itemBuilder: (_, _) => const ShimmerLoading(
                        width: 58,
                        height: 58,
                        borderRadius: 29,
                      ),
                    ),
                    error: (_, _) => const SizedBox.shrink(),
                  ),
                ),
              ),

              // ─── Promo banner ───
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
                  child: _PromoBanner(onTap: _openShop),
                ),
              ),

              // ─── Popular Products ───
              SliverToBoxAdapter(
                child: _SectionHeader(
                  title: 'Popular Products',
                  onSeeAll: _openShop,
                ),
              ),
              SliverToBoxAdapter(
                child: _PopularProductsGrid(
                  onProductTap: (product) =>
                      context.push('/products/${product.id}'),
                ),
              ),

              const SliverToBoxAdapter(child: SizedBox(height: 100)),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildProfileAvatar(bool isAuth) {
    final user = ref.watch(authProvider).user;
    final image = user?.profileImage?.trim() ?? '';
    final imageUrl = image.isEmpty
        ? ''
        : image.startsWith('http')
        ? image
        : '${ApiConfig.baseUrl}/uploads/$image';
    final palette = context.palette;

    return InkResponse(
      onTap: () => context.go(isAuth ? '/profile' : '/login'),
      radius: 25,
      child: CircleAvatar(
        radius: 21,
        backgroundColor: palette.primary.withValues(alpha: 0.12),
        backgroundImage: imageUrl.isNotEmpty
            ? CachedNetworkImageProvider(imageUrl)
            : null,
        child: imageUrl.isEmpty
            ? Icon(
                Icons.person_rounded,
                color: palette.primary,
                size: 23,
              )
            : null,
      ),
    );
  }

  OutlineInputBorder _pillBorder([Color? color]) {
    return OutlineInputBorder(
      borderRadius: BorderRadius.circular(28),
      borderSide: color == null
          ? BorderSide(color: context.palette.divider)
          : BorderSide(color: color, width: 1.2),
    );
  }
}

// ──────────────────────────────────────────────
// Header icon with count badge
// ──────────────────────────────────────────────
class _PopularProductsGrid extends ConsumerWidget {
  final ValueChanged<Product> onProductTap;

  const _PopularProductsGrid({required this.onProductTap});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(paginatedProductsProvider);
    final palette = context.palette;

    if (state.isLoading && state.products.isEmpty) {
      return Padding(
        padding: const EdgeInsets.all(32),
        child: Center(
          child: CircularProgressIndicator(color: palette.primary),
        ),
      );
    }

    if (state.error != null && state.products.isEmpty) {
      return Center(
        child: TextButton.icon(
          onPressed: () =>
              ref.read(paginatedProductsProvider.notifier).fetchFirstPage(),
          icon: const Icon(Icons.refresh_rounded, size: 18),
          label: const Text('Could not load popular products. Retry'),
        ),
      );
    }

    if (state.products.isEmpty) {
      return Padding(
        padding: const EdgeInsets.all(24),
        child: Center(
          child: Text(
            'No popular products available',
            style: TextStyle(color: palette.textSecondary, fontSize: 13),
          ),
        ),
      );
    }

    return Column(
      children: [
        GridView.builder(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          itemCount: state.products.length,
          gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: 2,
            crossAxisSpacing: 12,
            mainAxisSpacing: 12,
            childAspectRatio: 0.69,
          ),
          itemBuilder: (context, index) {
            final product = state.products[index];
            return _ProductTile(
              product: product,
              imageHeight: _ProductTile.imageHeightLarge,
              onTap: () => onProductTap(product),
            );
          },
        ),
        if (state.isLoadingMore)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 18),
            child: CircularProgressIndicator(
              strokeWidth: 2.5,
              color: palette.primary,
            ),
          ),
      ],
    );
  }
}


class _BadgeIconButton extends StatelessWidget {
  final IconData icon;
  final int count;
  final VoidCallback onTap;

  const _BadgeIconButton({
    required this.icon,
    required this.count,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final palette = context.palette;
    return InkResponse(
      onTap: onTap,
      radius: 24,
      child: SizedBox(
        width: 42,
        height: 42,
        child: Stack(
          clipBehavior: Clip.none,
          alignment: Alignment.center,
          children: [
            Container(
              width: 42,
              height: 42,
              decoration: BoxDecoration(
                color: palette.surface,
                shape: BoxShape.circle,
                border: Border.all(color: palette.divider),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.04),
                    blurRadius: 8,
                    offset: const Offset(0, 3),
                  ),
                ],
              ),
              child: Icon(icon, size: 21, color: palette.textPrimary),
            ),
            if (count > 0)
              Positioned(
                top: 5,
                right: 4,
                child: Container(
                  height: 17,
                  constraints: const BoxConstraints(minWidth: 17),
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  decoration: BoxDecoration(
                    color: palette.accent,
                    borderRadius: BorderRadius.circular(9),
                    border: Border.all(color: palette.surface, width: 1.5),
                  ),
                  child: Center(
                    child: Text(
                      count > 9 ? '9+' : '$count',
                      style: TextStyle(
                        color: palette.onAccent,
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
}

// ──────────────────────────────────────────────
// Section header ("Just for You ........ See All >")
// ──────────────────────────────────────────────
class _SectionHeader extends StatelessWidget {
  final String title;
  final VoidCallback? onSeeAll;

  const _SectionHeader({required this.title, this.onSeeAll});

  @override
  Widget build(BuildContext context) {
    final palette = context.palette;
    return Padding(
      padding: const EdgeInsets.fromLTRB(18, 24, 16, 12),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Expanded(
            child: Text(
              title,
              style: TextStyle(
                fontSize: 19,
                fontWeight: FontWeight.w800,
                color: palette.textPrimary,
                letterSpacing: -0.3,
              ),
            ),
          ),
          if (onSeeAll != null)
            GestureDetector(
              onTap: onSeeAll,
              behavior: HitTestBehavior.opaque,
              child: Padding(
                padding: EdgeInsets.symmetric(horizontal: 4, vertical: 4),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      'See all',
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w700,
                        color: palette.primary,
                      ),
                    ),
                    const SizedBox(width: 3),
                    Icon(
                      Icons.arrow_forward_ios_rounded,
                      size: 11,
                      color: palette.primary,
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}

// ──────────────────────────────────────────────
// Hero carousel (auto-advancing)
// ──────────────────────────────────────────────
class _HeroCarousel extends StatefulWidget {
  final List<HeroSlide> slides;
  final ValueChanged<String?> onOpen;

  const _HeroCarousel({required this.slides, required this.onOpen});

  @override
  State<_HeroCarousel> createState() => _HeroCarouselState();
}

class _HeroCarouselState extends State<_HeroCarousel> {
  final _controller = PageController();
  int _index = 0;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _restartAutoPlay();
  }

  @override
  void didUpdateWidget(covariant _HeroCarousel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.slides.length != widget.slides.length) {
      _index = _index.clamp(0, widget.slides.length - 1);
      _restartAutoPlay();
    }
  }

  // Restarted after every page change, so a manual swipe gets a full 5 seconds
  void _restartAutoPlay() {
    _timer?.cancel();
    if (widget.slides.length < 2) return;
    _timer = Timer.periodic(const Duration(seconds: 5), (_) {
      if (!_controller.hasClients) return;
      _controller.animateToPage(
        (_index + 1) % widget.slides.length,
        duration: const Duration(milliseconds: 450),
        curve: Curves.easeOutCubic,
      );
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 176,
      child: Stack(
        children: [
          PageView.builder(
            controller: _controller,
            itemCount: widget.slides.length,
            onPageChanged: (i) {
              setState(() => _index = i);
              _restartAutoPlay();
            },
            itemBuilder: (context, i) => Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: _HeroSlideCard(
                slide: widget.slides[i],
                onOpen: widget.onOpen,
              ),
            ),
          ),
          if (widget.slides.length > 1)
            Positioned(
              left: 0,
              right: 0,
              bottom: 12,
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: List.generate(widget.slides.length, (i) {
                  final active = i == _index;
                  return AnimatedContainer(
                    duration: const Duration(milliseconds: 250),
                    margin: const EdgeInsets.symmetric(horizontal: 3),
                    width: active ? 16 : 6,
                    height: 6,
                    decoration: BoxDecoration(
                      color: Colors.white.withValues(alpha: active ? 1 : 0.5),
                      borderRadius: BorderRadius.circular(3),
                    ),
                  );
                }),
              ),
            ),
        ],
      ),
    );
  }
}

class _HeroSlideCard extends StatelessWidget {
  final HeroSlide slide;
  final ValueChanged<String?> onOpen;

  const _HeroSlideCard({required this.slide, required this.onOpen});

  static Color? _parseColor(String? hex) {
    if (hex == null || hex.isEmpty) return null;
    try {
      final cleaned = hex.replaceAll('#', '');
      return Color(int.parse('FF$cleaned', radix: 16));
    } catch (_) {
      return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    final base = _parseColor(slide.accentColor) ?? const Color(0xFF1E1E22);
    // Keep text readable on light accent colors too. The slide color comes
    // from the server, not the theme, so the dark ink stays fixed instead of
    // following palette.textPrimary (which turns light in the dark style).
    final isLight = base.computeLuminance() > 0.5;
    const ink = AppColors.textPrimary;
    final fg = isLight ? ink : Colors.white;
    final fgMuted = fg.withValues(alpha: 0.75);
    final hasImage = slide.imageUrl != null && slide.imageUrl!.isNotEmpty;

    return GestureDetector(
      onTap: () => onOpen(slide.primaryLink),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(18),
        child: ColoredBox(
          color: base,
          child: Stack(
            fit: StackFit.expand,
            children: [
              // Photo on the right, fading into the base color behind the text
              if (hasImage)
                Align(
                  alignment: Alignment.centerRight,
                  child: FractionallySizedBox(
                    widthFactor: 0.62,
                    heightFactor: 1,
                    child: CachedNetworkImage(
                      imageUrl: slide.imageUrl!,
                      fit: BoxFit.cover,
                      errorWidget: (_, _, _) => const SizedBox.shrink(),
                    ),
                  ),
                ),
              DecoratedBox(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.centerLeft,
                    end: Alignment.centerRight,
                    colors: [base, base, base.withValues(alpha: 0)],
                    stops: const [0, 0.4, 0.72],
                  ),
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(18, 16, 18, 26),
                child: FractionallySizedBox(
                  widthFactor: hasImage ? 0.62 : 1,
                  alignment: Alignment.centerLeft,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      if (slide.eyebrow != null && slide.eyebrow!.isNotEmpty)
                        Text(
                          slide.eyebrow!.toUpperCase(),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            color: fgMuted,
                            fontSize: 9.5,
                            fontWeight: FontWeight.w600,
                            letterSpacing: 1.6,
                          ),
                        ),
                      if (slide.title != null && slide.title!.isNotEmpty) ...[
                        const SizedBox(height: 6),
                        Text(
                          slide.title!,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: GoogleFonts.playfairDisplay(
                            color: fg,
                            fontSize: 22,
                            fontWeight: FontWeight.w600,
                            height: 1.15,
                          ),
                        ),
                      ],
                      if (slide.subtitle != null &&
                          slide.subtitle!.isNotEmpty) ...[
                        const SizedBox(height: 6),
                        Text(
                          slide.subtitle!,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            color: fgMuted,
                            fontSize: 11.5,
                            height: 1.35,
                          ),
                        ),
                      ],
                      const SizedBox(height: 12),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 14,
                          vertical: 8,
                        ),
                        decoration: BoxDecoration(
                          color: isLight ? ink : Colors.white,
                          borderRadius: BorderRadius.circular(20),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(
                              (slide.primaryLabel?.isNotEmpty ?? false)
                                  ? slide.primaryLabel!
                                  : 'Explore Now',
                              style: TextStyle(
                                color: isLight ? Colors.white : ink,
                                fontSize: 11.5,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            const SizedBox(width: 6),
                            Icon(
                              Icons.arrow_forward_rounded,
                              size: 14,
                              color: isLight ? Colors.white : ink,
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ──────────────────────────────────────────────
// Product tile (horizontal list + grid)
// ──────────────────────────────────────────────
class _ProductTile extends StatelessWidget {
  static const double imageHeightSmall = 104;
  static const double imageHeightLarge = 150;
  // Name + price + rating block under the image
  static const double infoHeight = 84;

  final Product product;
  final double? width;
  final double imageHeight;
  final VoidCallback onTap;

  const _ProductTile({
    required this.product,
    required this.imageHeight,
    required this.onTap,
    this.width,
  });

  @override
  Widget build(BuildContext context) {
    final palette = context.palette;
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: width,
        decoration: BoxDecoration(
          color: palette.surface,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: palette.divider),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.045),
              blurRadius: 14,
              offset: const Offset(0, 6),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            ClipRRect(
              borderRadius: const BorderRadius.vertical(
                top: Radius.circular(13),
              ),
              child: Container(
                height: imageHeight,
                width: double.infinity,
                color: palette.surfaceVariant.withValues(alpha: 0.45),
                child: Stack(
                  fit: StackFit.expand,
                  children: [
                    if (product.imageUrl != null &&
                        product.imageUrl!.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.all(12),
                        child: CachedNetworkImage(
                          imageUrl: product.imageUrl!,
                          fit: BoxFit.contain,
                          placeholder: (context, url) => Center(
                            child: SizedBox(
                              width: 22,
                              height: 22,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                valueColor: AlwaysStoppedAnimation<Color>(
                                  palette.primary,
                                ),
                              ),
                            ),
                          ),
                          errorWidget: (_, _, _) => Icon(
                            Icons.image_not_supported_outlined,
                            color: palette.textHint,
                          ),
                        ),
                      )
                    else
                      Icon(
                        Icons.image_outlined,
                        color: palette.textHint,
                        size: 32,
                      ),
                    Positioned(
                      top: 6,
                      right: 6,
                      child: WishlistButton(
                        productId: product.id,
                        initialLiked: product.liked,
                        size: 15,
                      ),
                    ),
                    if (!product.inStock)
                      Positioned(
                        top: 8,
                        left: 8,
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 6,
                            vertical: 3,
                          ),
                          decoration: BoxDecoration(
                            color: Colors.black.withValues(alpha: 0.65),
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: const Text(
                            'Sold out',
                            style: TextStyle(
                              color: Colors.white,
                              fontSize: 9.5,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(11, 10, 11, 10),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    product.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 12.5,
                      fontWeight: FontWeight.w600,
                      // primaryDark is too dim on the dark style's surface
                      color: palette.isDark
                          ? palette.primaryLight
                          : palette.primaryDark,
                    ),
                  ),
                  const SizedBox(height: 5),
                  Row(
                    children: [
                      Text(
                        '\$${product.price.toStringAsFixed(2)}',
                        style: TextStyle(
                          fontSize: 13.5,
                          fontWeight: FontWeight.w700,
                          color: palette.textPrimary,
                        ),
                      ),
                      if (product.discountPercent > 0 &&
                          product.originalPrice > product.price) ...[
                        const SizedBox(width: 5),
                        Text(
                          '\$${product.originalPrice.toStringAsFixed(2)}',
                          style: TextStyle(
                            fontSize: 10,
                            color: palette.textSecondary,
                            decoration: TextDecoration.lineThrough,
                          ),
                        ),
                        const SizedBox(width: 4),
                        Text(
                          '-${product.discountPercent}%',
                          style: TextStyle(
                            fontSize: 10,
                            fontWeight: FontWeight.w700,
                            color: palette.accent,
                          ),
                        ),
                      ],
                    ],
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      const Icon(
                        Icons.star_rounded,
                        size: 14,
                        color: AppColors.starFilled,
                      ),
                      const SizedBox(width: 2),
                      Text(
                        product.reviewCount > 0
                            ? product.rating.toStringAsFixed(1)
                            : 'New',
                        style: TextStyle(
                          fontSize: 11,
                          color: palette.textSecondary,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ──────────────────────────────────────────────
// Category circle
// ──────────────────────────────────────────────
class _CategoryItem extends StatelessWidget {
  final Category category;
  final VoidCallback onTap;

  const _CategoryItem({required this.category, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final hasImage = category.imageUrl != null && category.imageUrl!.isNotEmpty;
    final palette = context.palette;
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: SizedBox(
        width: 78,
        child: Column(
          children: [
            Container(
              width: 60,
              height: 60,
              decoration: BoxDecoration(
                color: palette.surfaceVariant,
                shape: BoxShape.circle,
                border: Border.all(color: palette.divider),
                boxShadow: [
                  BoxShadow(
                    color: palette.primary.withValues(alpha: 0.06),
                    blurRadius: 8,
                    offset: const Offset(0, 3),
                  ),
                ],
              ),
              child: ClipOval(
                child: hasImage
                    ? CachedNetworkImage(
                        imageUrl: category.imageUrl!,
                        fit: BoxFit.cover,
                        errorWidget: (_, _, _) => Icon(
                          Icons.category_outlined,
                          color: palette.primary,
                        ),
                      )
                    : Icon(
                        Icons.category_outlined,
                        color: palette.primary,
                      ),
              ),
            ),
            const SizedBox(height: 6),
            Text(
              category.name,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 11.5,
                fontWeight: FontWeight.w500,
                color: palette.textPrimary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ──────────────────────────────────────────────
// Promo banner
// ──────────────────────────────────────────────
class _PromoBanner extends StatelessWidget {
  final VoidCallback onTap;

  const _PromoBanner({required this.onTap});

  @override
  Widget build(BuildContext context) {
    final palette = context.palette;
    final onAccent = palette.onAccent;
    return GestureDetector(
      onTap: onTap,
      child: Container(
        height: 84,
        decoration: BoxDecoration(
          gradient: LinearGradient(
            colors: [palette.accent, palette.accent.withValues(alpha: 0.8)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(16),
        ),
        clipBehavior: Clip.antiAlias,
        child: Stack(
          children: [
            // Decorative bags
            Positioned(
              right: 58,
              bottom: -14,
              child: Transform.rotate(
                angle: -0.2,
                child: Icon(
                  Icons.shopping_bag_rounded,
                  size: 72,
                  color: Colors.white.withValues(alpha: 0.18),
                ),
              ),
            ),
            Positioned(
              right: 100,
              top: -10,
              child: Icon(
                Icons.local_mall_rounded,
                size: 44,
                color: Colors.white.withValues(alpha: 0.14),
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 18),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text(
                          'NEW ARRIVALS',
                          style: TextStyle(
                            color: onAccent.withValues(alpha: 0.7),
                            fontSize: 9.5,
                            fontWeight: FontWeight.w600,
                            letterSpacing: 1.6,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'Fresh styles just landed',
                          style: TextStyle(
                            color: onAccent,
                            fontSize: 16,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Container(
                    width: 36,
                    height: 36,
                    decoration: const BoxDecoration(
                      color: Colors.white,
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      Icons.arrow_forward_rounded,
                      size: 18,
                      color: palette.accent,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

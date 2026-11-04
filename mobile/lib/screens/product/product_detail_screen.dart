import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:share_plus/share_plus.dart';

import '../../config/theme.dart';
import '../../models/models.dart';
import '../../providers/providers.dart';
import '../../widgets/common_widgets.dart';
import 'variant_matrix.dart';

class ProductDetailScreen extends ConsumerStatefulWidget {
  final int productId;

  const ProductDetailScreen({super.key, required this.productId});

  @override
  ConsumerState<ProductDetailScreen> createState() =>
      _ProductDetailScreenState();
}

class _ProductDetailScreenState extends ConsumerState<ProductDetailScreen> {
  // Store-wide promises shown under the product options. Keep these in line
  // with the shop's real policies.
  static const _benefits = [
    (icon: Icons.local_shipping_outlined, labelKey: 'fastDelivery'),
    (icon: Icons.verified_outlined, labelKey: 'trustedQuality'),
    (icon: Icons.lock_outline_rounded, labelKey: 'secureCheckout'),
  ];

  static const _galleryAspectRatio = 1.4;

  // Product photos usually have white backgrounds, so in dark mode they sit
  // on a light stage instead of showing up as white boxes.
  static const _darkModePhotoStage = Color(0xFFE6E8EB);

  final _pageController = PageController();
  final _reviewsKey = GlobalKey();

  int _imageIndex = 0;
  int _quantity = 1;
  // One chosen value per option group (e.g. ['Black', 'One Size']).
  List<String>? _selectedOptions;
  bool _isAddingToCart = false;
  bool _isBuyingNow = false;
  bool _descriptionExpanded = false;

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  // build() watches appLanguageProvider, so reading it here stays current.
  String _t(String key) => appText(ref.read(appLanguageProvider), key);

  String _money(double value) => '\$${value.toStringAsFixed(2)}';

  @override
  Widget build(BuildContext context) {
    final productAsync = ref.watch(productDetailProvider(widget.productId));
    final cartCount = ref.watch(cartProvider.select((c) => c.totalQuantity));
    ref.watch(appLanguageProvider);
    final palette = context.palette;

    return Scaffold(
      backgroundColor: palette.surface,
      appBar: AppBar(
        leading: const AppBackButton(fallbackRoute: '/home'),
        title: FittedBox(
          fit: BoxFit.scaleDown,
          child: Text(_t('productDetails')),
        ),
        actions: [
          if (productAsync.value case final product?)
            _buildAppBarWishlistButton(product),
          _buildCartButton(cartCount),
          const SizedBox(width: 6),
        ],
      ),
      body: productAsync.when(
        data: _buildContent,
        loading: _buildLoadingSkeleton,
        error: (err, _) => EmptyState(
          icon: Icons.error_outline_rounded,
          title: _t('failedLoadProduct'),
          subtitle: err.toString(),
          actionLabel: _t('retry'),
          onAction: () => ref.invalidate(productDetailProvider(widget.productId)),
        ),
      ),
      bottomNavigationBar: productAsync.whenOrNull(data: _buildBottomBar),
    );
  }

  // ──────────────────────────────────────────────
  // Selection & pricing
  // ──────────────────────────────────────────────

  /// Resolves the chosen variant and the price/stock to display, picking a
  /// default variant the first time (or when the product data changed shape).
  _Selection _resolve(Product product) {
    final matrix = product.variants.isEmpty
        ? null
        : VariantMatrix.of(product);
    ProductVariant? variant;
    if (matrix != null) {
      final current = _selectedOptions;
      if (current == null ||
          current.length != matrix.groupCount ||
          matrix.match(current) == null) {
        _selectedOptions = matrix.initialSelection();
      }
      variant = matrix.match(_selectedOptions!);
    }

    final price = variant?.price ?? product.price;
    final originalPrice = variant?.originalPrice ?? product.originalPrice;
    var discount = variant?.discountPercent ?? product.discountPercent;
    if (discount <= 0 && originalPrice > price && originalPrice > 0) {
      discount = ((1 - price / originalPrice) * 100).round();
    }

    final stock = variant?.stockQuantity ?? product.stockQuantity;
    // Stock can drop on a refetch (pull-to-refresh, wishlist toggle).
    if (stock > 0 && _quantity > stock) _quantity = stock;

    return _Selection(
      matrix: matrix,
      variant: variant,
      price: price,
      originalPrice: originalPrice,
      discountPercent: discount,
      inStock: variant != null
          ? variant.enabled && variant.inStock
          : matrix == null && product.inStock,
      stock: stock,
    );
  }

  List<String> _galleryImages(Product product) {
    final images = <String>[];
    void add(String? url) {
      if (url != null && url.isNotEmpty && !images.contains(url)) {
        images.add(url);
      }
    }

    add(product.imageUrl);
    for (final url in product.galleryUrls) {
      add(url);
    }
    for (final variant in product.variants) {
      add(variant.imageUrl);
    }
    return images;
  }

  void _selectOption(
    Product product,
    VariantMatrix matrix,
    int group,
    String value,
  ) {
    final next = matrix.select(group, value, _selectedOptions!);
    final variant = matrix.match(next);
    HapticFeedback.selectionClick();
    setState(() {
      _selectedOptions = next;
      final stock = variant?.stockQuantity ?? 0;
      if (_quantity > stock) _quantity = stock > 0 ? stock : 1;
    });

    // Show the photo of the chosen variant, if it has one.
    final image = variant?.imageUrl;
    if (image == null || image.isEmpty || !_pageController.hasClients) return;
    final index = _galleryImages(product).indexOf(image);
    if (index >= 0 && index != _imageIndex) {
      _pageController.animateToPage(
        index,
        duration: const Duration(milliseconds: 350),
        curve: Curves.easeOutCubic,
      );
    }
  }

  // ──────────────────────────────────────────────
  // Content
  // ──────────────────────────────────────────────

  Widget _buildContent(Product product) {
    final palette = context.palette;
    final selection = _resolve(product);
    final matrix = selection.matrix;
    final description = product.description?.trim() ?? '';

    return RefreshIndicator(
      color: palette.primary,
      onRefresh: () async {
        ref.invalidate(reviewsProvider(widget.productId));
        ref.invalidate(reviewSummaryProvider(widget.productId));
        ref.invalidate(productDetailProvider(widget.productId));
        try {
          await ref.read(productDetailProvider(widget.productId).future);
        } catch (_) {
          // The error state is rendered by the provider's AsyncValue.
        }
      },
      child: SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildGallery(product, selection),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 20, 20, 24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    product.name,
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w700,
                      color: palette.textPrimary,
                      height: 1.3,
                      letterSpacing: -0.2,
                    ),
                  ),
                  const SizedBox(height: 10),
                  _buildPriceRow(product, selection),
                  if (description.isNotEmpty) ...[
                    const SizedBox(height: 14),
                    _buildDescription(description),
                  ],
                  if (matrix != null)
                    for (var group = 0; group < matrix.groupCount; group++) ...[
                      const SizedBox(height: 22),
                      _buildOptionGroup(product, matrix, group),
                    ],
                  const SizedBox(height: 22),
                  _buildQuantityRow(selection),
                ],
              ),
            ),
            _buildBenefits(),
            Padding(
              key: _reviewsKey,
              padding: const EdgeInsets.fromLTRB(20, 24, 20, 32),
              child: _buildReviewsSection(product),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildGallery(Product product, _Selection selection) {
    final palette = context.palette;
    final images = _galleryImages(product);
    final screen = MediaQuery.sizeOf(context);
    // Capped so landscape phones and tablets still see the details.
    final height = (screen.width / _galleryAspectRatio).clamp(
      0.0,
      screen.height * 0.5,
    );
    final stage = palette.isDark ? _darkModePhotoStage : palette.surfaceVariant;
    final dotInk = palette.isDark ? const Color(0xFF111111) : palette.textPrimary;

    return SizedBox(
      width: double.infinity,
      height: height,
      child: ColoredBox(
        color: stage,
        child: Stack(
          children: [
            Positioned.fill(
              child: images.isEmpty
                  ? _imagePlaceholder(size: 64)
                  : PageView.builder(
                      controller: _pageController,
                      itemCount: images.length,
                      onPageChanged: (i) => setState(() => _imageIndex = i),
                      itemBuilder: (context, index) => Padding(
                        padding: const EdgeInsets.fromLTRB(24, 14, 24, 30),
                        child: CachedNetworkImage(
                          imageUrl: images[index],
                          fit: BoxFit.contain,
                          // Multiply blends white photo backgrounds into the
                          // light stage.
                          color: stage,
                          colorBlendMode: BlendMode.multiply,
                          placeholder: (_, _) => Center(
                            child: SizedBox(
                              width: 24,
                              height: 24,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: palette.textHint,
                              ),
                            ),
                          ),
                          errorWidget: (_, _, _) => _imagePlaceholder(size: 64),
                        ),
                      ),
                    ),
            ),
            if (selection.hasDiscount)
              Positioned(
                top: 16,
                left: 16,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: palette.primary,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 4,
                    ),
                    child: Text(
                      '-${selection.discountPercent}%',
                      style: TextStyle(
                        color: palette.onPrimary,
                        fontSize: 13,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                ),
              ),
            Positioned(
              top: 16,
              right: 16,
              child: Builder(
                builder: (buttonContext) => Material(
                  color: palette.surface,
                  borderRadius: BorderRadius.circular(10),
                  child: InkWell(
                    borderRadius: BorderRadius.circular(10),
                    onTap: () =>
                        _shareProduct(buttonContext, product, selection.price),
                    child: Tooltip(
                      message: _t('share'),
                      child: SizedBox(
                        width: 36,
                        height: 36,
                        child: Icon(
                          Icons.ios_share_rounded,
                          size: 18,
                          color: palette.textPrimary,
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
            if (images.length > 1)
              Positioned(
                bottom: 14,
                left: 0,
                right: 0,
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: List.generate(images.length, (index) {
                    final isActive = index == _imageIndex;
                    return GestureDetector(
                      behavior: HitTestBehavior.opaque,
                      onTap: () => _pageController.animateToPage(
                        index,
                        duration: const Duration(milliseconds: 300),
                        curve: Curves.easeOutCubic,
                      ),
                      child: Padding(
                        padding: const EdgeInsets.all(4),
                        child: AnimatedContainer(
                          duration: const Duration(milliseconds: 200),
                          width: 7,
                          height: 7,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: isActive
                                ? dotInk
                                : dotInk.withValues(alpha: 0.25),
                          ),
                        ),
                      ),
                    );
                  }),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _imagePlaceholder({double size = 40}) {
    return Center(
      child: Icon(
        Icons.image_not_supported_outlined,
        size: size,
        color: context.palette.textHint,
      ),
    );
  }

  Widget _buildPriceRow(Product product, _Selection selection) {
    final palette = context.palette;
    return LayoutBuilder(
      builder: (context, constraints) => Row(
        children: [
          Expanded(
            child: Wrap(
              spacing: 10,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                Text(
                  _money(selection.price),
                  softWrap: false,
                  style: TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.w800,
                    color: palette.textPrimary,
                  ),
                ),
                if (selection.hasDiscount)
                  Text(
                    _money(selection.originalPrice),
                    softWrap: false,
                    style: TextStyle(
                      fontSize: 15,
                      color: palette.textHint,
                      decoration: TextDecoration.lineThrough,
                      decorationColor: palette.textHint,
                    ),
                  ),
              ],
            ),
          ),
          if (product.reviewCount > 0)
            // Capped so long or large-scale review text can't squeeze the
            // price; the review count ellipsizes instead.
            ConstrainedBox(
              constraints: BoxConstraints(
                maxWidth: constraints.maxWidth * 0.55,
              ),
              child: InkWell(
                onTap: _scrollToReviews,
                borderRadius: BorderRadius.circular(8),
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 4,
                    vertical: 4,
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(
                        Icons.star_rounded,
                        size: 20,
                        color: AppColors.starFilled,
                      ),
                      const SizedBox(width: 4),
                      Text(
                        product.rating.toStringAsFixed(1),
                        style: TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w600,
                          color: palette.textPrimary,
                        ),
                      ),
                      const SizedBox(width: 6),
                      Flexible(
                        child: Text(
                          '(${product.reviewCount} ${_t('reviews')})',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 14,
                            color: palette.textSecondary,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }

  void _scrollToReviews() {
    final reviewsContext = _reviewsKey.currentContext;
    if (reviewsContext == null) return;
    Scrollable.ensureVisible(
      reviewsContext,
      duration: const Duration(milliseconds: 400),
      curve: Curves.easeOutCubic,
    );
  }

  Widget _buildDescription(String text) {
    final palette = context.palette;
    final style = TextStyle(
      fontSize: 14.5,
      height: 1.55,
      color: palette.textSecondary,
    );

    return LayoutBuilder(
      builder: (context, constraints) {
        var measured = DefaultTextStyle.of(context).style.merge(style);
        // Text renders bold under the OS "Bold text" setting; measure it so.
        if (MediaQuery.boldTextOf(context)) {
          measured = measured.merge(
            const TextStyle(fontWeight: FontWeight.bold),
          );
        }
        final painter = TextPainter(
          text: TextSpan(text: text, style: measured),
          maxLines: 3,
          textDirection: Directionality.of(context),
          textScaler: MediaQuery.textScalerOf(context),
        )..layout(maxWidth: constraints.maxWidth);
        final overflows = painter.didExceedMaxLines;
        painter.dispose();

        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            AnimatedSize(
              duration: const Duration(milliseconds: 200),
              alignment: Alignment.topCenter,
              child: Text(
                text,
                style: style,
                maxLines: _descriptionExpanded ? null : 3,
                overflow: _descriptionExpanded
                    ? TextOverflow.visible
                    : TextOverflow.ellipsis,
              ),
            ),
            if (overflows)
              InkWell(
                onTap: () => setState(
                  () => _descriptionExpanded = !_descriptionExpanded,
                ),
                child: Padding(
                  padding: const EdgeInsets.only(top: 6, bottom: 2),
                  child: Text(
                    _descriptionExpanded ? _t('showLess') : _t('readMore'),
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: palette.textPrimary,
                    ),
                  ),
                ),
              ),
          ],
        );
      },
    );
  }

  Widget _buildOptionGroup(Product product, VariantMatrix matrix, int group) {
    final palette = context.palette;
    final selected = _selectedOptions!;
    final name = matrix.names[group].isEmpty
        ? _t('option')
        : matrix.names[group];
    final values = matrix.valuesFor(group);
    final showImages = matrix.showsImages(group);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text.rich(
          TextSpan(
            children: [
              TextSpan(
                text: '$name: ',
                style: TextStyle(
                  fontWeight: FontWeight.w700,
                  color: palette.textPrimary,
                ),
              ),
              TextSpan(
                text: selected[group],
                style: TextStyle(color: palette.textSecondary),
              ),
            ],
          ),
          style: const TextStyle(fontSize: 15),
        ),
        const SizedBox(height: 12),
        Wrap(
          spacing: 12,
          runSpacing: 12,
          children: [
            for (final value in values)
              _buildOptionTile(
                value: value,
                imageUrl: showImages
                    ? matrix.imageFor(group, value, selected)
                    : null,
                showImage: showImages,
                isSelected: selected[group] == value,
                isAvailable: matrix.isAvailable(group, value, selected),
                onTap: () => _selectOption(product, matrix, group, value),
              ),
          ],
        ),
      ],
    );
  }

  Widget _buildOptionTile({
    required String value,
    required String? imageUrl,
    required bool showImage,
    required bool isSelected,
    required bool isAvailable,
    required VoidCallback onTap,
  }) {
    final palette = context.palette;
    final tileColor = showImage && palette.isDark
        ? _darkModePhotoStage
        : palette.surface;
    final labelStyle = TextStyle(
      fontSize: showImage ? 11 : 14,
      fontWeight: isSelected ? FontWeight.w600 : FontWeight.w500,
      color: palette.textPrimary,
      decoration: isAvailable ? null : TextDecoration.lineThrough,
      decorationColor: palette.textSecondary,
    );

    final Widget content;
    if (!showImage) {
      content = Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        child: Text(value, style: labelStyle),
      );
    } else {
      content = SizedBox(
        width: 60,
        height: 60,
        child: Padding(
          padding: const EdgeInsets.all(5),
          child: imageUrl != null
              ? CachedNetworkImage(
                  imageUrl: imageUrl,
                  fit: BoxFit.contain,
                  color: tileColor,
                  colorBlendMode: BlendMode.multiply,
                  errorWidget: (_, _, _) => _imagePlaceholder(size: 22),
                )
              : Center(
                  child: Text(
                    value,
                    style: labelStyle,
                    maxLines: 2,
                    textAlign: TextAlign.center,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
        ),
      );
    }

    // One node per tile that screen readers can activate; the visual
    // content (image or text) is excluded so it isn't read twice.
    return Semantics(
      container: true,
      button: true,
      selected: isSelected,
      label: isAvailable ? value : '$value, ${_t('outOfStock')}',
      onTap: onTap,
      excludeSemantics: true,
      child: Opacity(
        opacity: isAvailable ? 1 : 0.45,
        child: Material(
          color: tileColor,
          clipBehavior: Clip.antiAlias,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(8),
            side: BorderSide(
              color: isSelected ? palette.primary : palette.divider,
              width: isSelected ? 1.6 : 1,
            ),
          ),
          child: InkWell(onTap: onTap, child: content),
        ),
      ),
    );
  }

  Widget _buildQuantityRow(_Selection selection) {
    final palette = context.palette;

    Widget stepButton(IconData icon, VoidCallback? onPressed) {
      return IconButton(
        icon: Icon(icon, size: 18),
        onPressed: onPressed,
        color: palette.textPrimary,
        disabledColor: palette.textHint,
        padding: EdgeInsets.zero,
        constraints: const BoxConstraints.tightFor(width: 40, height: 40),
      );
    }

    return Row(
      children: [
        Text(
          _t('quantity'),
          style: TextStyle(
            fontSize: 15,
            fontWeight: FontWeight.w700,
            color: palette.textPrimary,
          ),
        ),
        const Spacer(),
        DecoratedBox(
          decoration: BoxDecoration(
            border: Border.all(color: palette.divider),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              stepButton(
                Icons.remove_rounded,
                _quantity > 1 ? () => setState(() => _quantity--) : null,
              ),
              ConstrainedBox(
                constraints: const BoxConstraints(minWidth: 32),
                child: Text(
                  '$_quantity',
                  softWrap: false,
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w700,
                    color: palette.textPrimary,
                  ),
                ),
              ),
              stepButton(
                Icons.add_rounded,
                selection.inStock && _quantity < selection.stock
                    ? () => setState(() => _quantity++)
                    : null,
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildBenefits() {
    final palette = context.palette;
    return DecoratedBox(
      decoration: BoxDecoration(
        border: Border.symmetric(
          horizontal: BorderSide(color: palette.divider),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 18),
        child: IntrinsicHeight(
          child: Row(
            children: [
              for (var i = 0; i < _benefits.length; i++) ...[
                if (i > 0)
                  VerticalDivider(
                    width: 1,
                    thickness: 1,
                    color: palette.divider,
                  ),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 6),
                    child: Column(
                      children: [
                        Icon(
                          _benefits[i].icon,
                          size: 22,
                          color: palette.textSecondary,
                        ),
                        const SizedBox(height: 8),
                        Text(
                          _t(_benefits[i].labelKey),
                          textAlign: TextAlign.center,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 12.5,
                            height: 1.3,
                            color: palette.textSecondary,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  // ──────────────────────────────────────────────
  // Reviews
  // ──────────────────────────────────────────────

  Widget _buildReviewsSection(Product product) {
    final palette = context.palette;
    final reviewsAsync = ref.watch(reviewsProvider(widget.productId));
    final summaryAsync = ref.watch(reviewSummaryProvider(widget.productId));

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: Text(
                _t('ratingsReviews'),
                style: TextStyle(
                  fontSize: 17,
                  fontWeight: FontWeight.w700,
                  color: palette.textPrimary,
                ),
              ),
            ),
            if (product.reviewCount > 0)
              Text(
                '${product.reviewCount} ${_t('reviews')}',
                style: TextStyle(fontSize: 13, color: palette.textSecondary),
              ),
          ],
        ),
        const SizedBox(height: 16),
        summaryAsync.when(
          data: (summary) => summary.count == 0
              ? const SizedBox.shrink()
              : Padding(
                  padding: const EdgeInsets.only(bottom: 20),
                  child: _buildReviewSummary(summary),
                ),
          loading: () => const Padding(
            padding: EdgeInsets.only(bottom: 20),
            child: ShimmerLoading(height: 104, borderRadius: 16),
          ),
          error: (_, _) => const SizedBox.shrink(),
        ),
        reviewsAsync.when(
          data: (page) {
            if (page.content.isEmpty) {
              return Padding(
                padding: const EdgeInsets.symmetric(vertical: 12),
                child: Center(
                  child: Text(
                    _t('noReviewsYet'),
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: palette.textSecondary,
                      fontSize: 13.5,
                    ),
                  ),
                ),
              );
            }
            return Column(
              children: [
                for (var i = 0; i < page.content.length; i++) ...[
                  if (i > 0) const Divider(height: 28),
                  _buildReviewTile(page.content[i]),
                ],
              ],
            );
          },
          loading: () => const ShimmerLoading(height: 100),
          error: (_, _) => const SizedBox.shrink(),
        ),
      ],
    );
  }

  Widget _buildReviewSummary(ReviewSummary summary) {
    final palette = context.palette;
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: palette.surfaceVariant,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: [
          Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                summary.average.toStringAsFixed(1),
                style: TextStyle(
                  fontSize: 34,
                  fontWeight: FontWeight.w800,
                  color: palette.textPrimary,
                  height: 1.1,
                ),
              ),
              const SizedBox(height: 4),
              _buildStars(summary.average.round(), size: 15),
              const SizedBox(height: 4),
              Text(
                '${summary.count} ${_t('reviews')}',
                style: TextStyle(fontSize: 11.5, color: palette.textSecondary),
              ),
            ],
          ),
          const SizedBox(width: 24),
          Expanded(
            child: Column(
              children: [5, 4, 3, 2, 1].map((star) {
                final count = summary.distribution['$star'] ?? 0;
                final ratio = summary.count > 0 ? count / summary.count : 0.0;
                return Padding(
                  padding: const EdgeInsets.symmetric(vertical: 2.5),
                  child: Row(
                    children: [
                      SizedBox(
                        width: 10,
                        child: Text(
                          '$star',
                          style: TextStyle(
                            fontSize: 11.5,
                            fontWeight: FontWeight.w600,
                            color: palette.textSecondary,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: ClipRRect(
                          borderRadius: BorderRadius.circular(4),
                          child: LinearProgressIndicator(
                            value: ratio,
                            minHeight: 6,
                            backgroundColor: palette.divider,
                            valueColor: AlwaysStoppedAnimation(
                              palette.textPrimary,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      SizedBox(
                        width: 24,
                        child: Text(
                          '$count',
                          textAlign: TextAlign.end,
                          style: TextStyle(
                            fontSize: 11.5,
                            color: palette.textSecondary,
                          ),
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
    );
  }

  Widget _buildReviewTile(Review review) {
    final palette = context.palette;
    final avatar = review.authorAvatar;
    final hasAvatar = avatar != null && avatar.isNotEmpty;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            CircleAvatar(
              radius: 18,
              backgroundColor: palette.surfaceVariant,
              backgroundImage: hasAvatar
                  ? CachedNetworkImageProvider(avatar)
                  : null,
              child: hasAvatar
                  ? null
                  : Text(
                      review.authorName.isNotEmpty
                          ? review.authorName[0].toUpperCase()
                          : 'U',
                      style: TextStyle(
                        fontWeight: FontWeight.w700,
                        color: palette.textPrimary,
                        fontSize: 14,
                      ),
                    ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Flexible(
                        child: Text(
                          review.authorName,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontWeight: FontWeight.w600,
                            fontSize: 14,
                            color: palette.textPrimary,
                          ),
                        ),
                      ),
                      if (review.verifiedPurchase) ...[
                        const SizedBox(width: 5),
                        const Icon(
                          Icons.verified_rounded,
                          size: 15,
                          color: AppColors.success,
                        ),
                      ],
                    ],
                  ),
                  if (review.createdAt != null)
                    Text(
                      review.createdAt!.split('T').first,
                      style: TextStyle(color: palette.textHint, fontSize: 12),
                    ),
                ],
              ),
            ),
            _buildStars(review.rating, size: 14),
          ],
        ),
        if (review.variantLabel != null && review.variantLabel!.isNotEmpty) ...[
          const SizedBox(height: 8),
          Text(
            review.variantLabel!,
            style: TextStyle(fontSize: 12, color: palette.textHint),
          ),
        ],
        if (review.title != null && review.title!.isNotEmpty) ...[
          const SizedBox(height: 8),
          Text(
            review.title!,
            style: TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w600,
              color: palette.textPrimary,
            ),
          ),
        ],
        if (review.comment != null && review.comment!.isNotEmpty) ...[
          const SizedBox(height: 6),
          Text(
            review.comment!,
            style: TextStyle(
              fontSize: 13.5,
              color: palette.textSecondary,
              height: 1.45,
            ),
          ),
        ],
      ],
    );
  }

  Widget _buildStars(int filled, {required double size}) {
    final palette = context.palette;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: List.generate(5, (i) {
        final isFilled = i < filled;
        return Icon(
          isFilled ? Icons.star_rounded : Icons.star_outline_rounded,
          size: size,
          color: isFilled ? AppColors.starFilled : palette.starEmpty,
        );
      }),
    );
  }

  // ──────────────────────────────────────────────
  // App bar actions
  // ──────────────────────────────────────────────

  bool _isLiked(Product product) =>
      ref.watch(wishlistProvider)[product.id] ?? product.liked;

  Widget _buildHeartIcon(bool isLiked, {required double size}) {
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 200),
      transitionBuilder: (child, animation) =>
          ScaleTransition(scale: animation, child: child),
      child: Icon(
        isLiked ? Icons.favorite_rounded : Icons.favorite_border_rounded,
        key: ValueKey<bool>(isLiked),
        size: size,
        color: isLiked ? Colors.redAccent : context.palette.textPrimary,
      ),
    );
  }

  Widget _buildAppBarWishlistButton(Product product) {
    final isLiked = _isLiked(product);
    return IconButton(
      tooltip: _t(isLiked ? 'savedWishlist' : 'addWishlist'),
      onPressed: () => _toggleWishlist(product),
      icon: _buildHeartIcon(isLiked, size: 26),
    );
  }

  Widget _buildCartButton(int count) {
    final palette = context.palette;
    return IconButton(
      tooltip: _t('cart'),
      // /cart is a bottom-nav tab: go() switches to it. push() would stack a
      // second copy of the tab shell and trip GoRouter's duplicate-key assert.
      onPressed: () => context.go('/cart'),
      icon: Badge(
        isLabelVisible: count > 0,
        label: Text(count > 99 ? '99+' : '$count'),
        backgroundColor: palette.primary,
        textColor: palette.onPrimary,
        offset: const Offset(6, -6),
        child: Icon(
          Icons.shopping_bag_outlined,
          size: 26,
          color: palette.textPrimary,
        ),
      ),
    );
  }

  Future<void> _toggleWishlist(Product product) async {
    final auth = ref.read(authProvider);
    if (auth.status != AuthStatus.authenticated) {
      _showSignInPrompt(_t('pleaseSignIn'));
      return;
    }
    HapticFeedback.selectionClick();
    await ref
        .read(wishlistProvider.notifier)
        .toggle(product.id, currentlyLiked: product.liked);
  }

  Future<void> _shareProduct(
    BuildContext buttonContext,
    Product product,
    double price,
  ) async {
    final box = buttonContext.findRenderObject() as RenderBox?;
    final origin = box == null ? null : box.localToGlobal(Offset.zero) & box.size;
    try {
      await SharePlus.instance.share(
        ShareParams(
          text: '${product.name} — ${_money(price)}',
          subject: product.name,
          sharePositionOrigin: origin,
        ),
      );
    } catch (_) {
      // Sharing is best-effort; the platform sheet reports its own errors.
    }
  }

  void _showSignInPrompt(String message) {
    // Captured now: the snackbar can outlive this screen.
    final router = GoRouter.of(context);
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(message),
          action: SnackBarAction(
            label: _t('signIn'),
            onPressed: () => router.push('/login'),
          ),
          duration: const Duration(seconds: 3),
        ),
      );
  }

  // ──────────────────────────────────────────────
  // Bottom bar
  // ──────────────────────────────────────────────

  Widget _buildBottomBar(Product product) {
    final palette = context.palette;
    final selection = _resolve(product);
    // The stock line is small text, so on light surfaces the status colors
    // are deepened to stay readable (AppColors.success is ~2.5:1 on white).
    final inStockColor = palette.isDark
        ? AppColors.success
        : const Color(0xFF15803D);
    final noStockColor = palette.isDark
        ? AppColors.error
        : const Color(0xFFC0392B);
    final lowStock =
        selection.inStock && selection.stock > 0 && selection.stock <= 5;
    final (statusText, statusColor) = !selection.inStock
        ? (_t('outOfStock'), noStockColor)
        : lowStock
        ? (
            _t('onlyLeft').replaceAll('{n}', '${selection.stock}'),
            noStockColor,
          )
        : (_t('inStock'), inStockColor);
    final busy = _isAddingToCart || _isBuyingNow;
    final canBuy =
        selection.inStock &&
        !busy &&
        (selection.matrix == null || selection.variant != null);
    // Narrow phones drop the price column (the price is also shown under
    // the product name) so both buttons keep readable labels.
    final showPrice = MediaQuery.sizeOf(context).width >= 360;
    const buttonHeight = 48.0;
    final buttonShape = RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(8),
    );

    Widget label(String text, {required bool loading, required Color color}) {
      if (loading) {
        return SizedBox(
          width: 20,
          height: 20,
          child: CircularProgressIndicator(strokeWidth: 2.2, color: color),
        );
      }
      return Text(
        text,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
      );
    }

    return DecoratedBox(
      decoration: BoxDecoration(
        color: palette.surface,
        border: Border(top: BorderSide(color: palette.divider)),
      ),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
          child: Row(
            children: [
              if (showPrice) ...[
                Expanded(
                  flex: 3,
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      FittedBox(
                        fit: BoxFit.scaleDown,
                        alignment: Alignment.centerLeft,
                        child: Text(
                          _money(selection.price * _quantity),
                          style: TextStyle(
                            fontSize: 19,
                            fontWeight: FontWeight.w800,
                            color: palette.textPrimary,
                          ),
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        statusText,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                          color: statusColor,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 10),
              ],
              if (selection.inStock) ...[
                Expanded(
                  flex: 4,
                  child: SizedBox(
                    height: buttonHeight,
                    child: OutlinedButton(
                      onPressed: canBuy
                          ? () => _handleAddToCart(product, selection.variant)
                          : null,
                      style: OutlinedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(horizontal: 8),
                        shape: buttonShape,
                        side: BorderSide(
                          color: canBuy ? palette.primary : palette.divider,
                          width: 1.4,
                        ),
                      ),
                      child: label(
                        _t('addToCart'),
                        loading: _isAddingToCart,
                        color: palette.primary,
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  flex: 4,
                  child: SizedBox(
                    height: buttonHeight,
                    child: FilledButton(
                      onPressed: canBuy
                          ? () => _handleBuyNow(product, selection.variant)
                          : null,
                      style: FilledButton.styleFrom(
                        padding: const EdgeInsets.symmetric(horizontal: 8),
                        shape: buttonShape,
                      ),
                      child: label(
                        _t('buyNow'),
                        loading: _isBuyingNow,
                        color: palette.onPrimary,
                      ),
                    ),
                  ),
                ),
              ] else
                Expanded(
                  flex: 8,
                  child: SizedBox(
                    height: buttonHeight,
                    child: FilledButton(
                      onPressed: null,
                      style: FilledButton.styleFrom(shape: buttonShape),
                      child: label(
                        _t('outOfStock'),
                        loading: false,
                        color: palette.onPrimary,
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _handleAddToCart(
    Product product,
    ProductVariant? variant,
  ) async {
    final auth = ref.read(authProvider);
    if (auth.status != AuthStatus.authenticated) {
      _showSignInPrompt(_t('signInToAddCart'));
      return;
    }

    final messenger = ScaffoldMessenger.of(context);
    final router = GoRouter.of(context);
    final quantity = _quantity;
    setState(() => _isAddingToCart = true);
    final success = await ref
        .read(cartProvider.notifier)
        .addItem(product.id, quantity, variantId: variant?.id);

    if (!mounted) return;
    setState(() => _isAddingToCart = false);

    messenger
      ..hideCurrentSnackBar()
      ..clearSnackBars();

    if (success) {
      HapticFeedback.lightImpact();
      messenger.showSnackBar(
        SnackBar(
          content: Row(
            children: [
              const Icon(
                Icons.check_circle_rounded,
                color: AppColors.success,
                size: 20,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  '${_t('addedToCart')} · $quantity × ${product.name}',
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
          action: SnackBarAction(
            label: _t('viewCart'),
            // A tab route, so go() (see _buildCartButton).
            onPressed: () => router.go('/cart'),
          ),
          duration: const Duration(seconds: 4),
        ),
      );
    } else {
      messenger.showSnackBar(
        SnackBar(
          content: Text(_t('addToCartFailed')),
          backgroundColor: AppColors.error,
          duration: const Duration(seconds: 3),
        ),
      );
    }
  }

  /// Adds the selection to the cart and goes straight to checkout.
  Future<void> _handleBuyNow(Product product, ProductVariant? variant) async {
    final auth = ref.read(authProvider);
    if (auth.status != AuthStatus.authenticated) {
      _showSignInPrompt(_t('signInToAddCart'));
      return;
    }

    final messenger = ScaffoldMessenger.of(context);
    final router = GoRouter.of(context);
    setState(() => _isBuyingNow = true);
    final success = await ref
        .read(cartProvider.notifier)
        .addItem(product.id, _quantity, variantId: variant?.id);

    if (!mounted) return;
    setState(() => _isBuyingNow = false);

    if (success) {
      messenger.hideCurrentSnackBar();
      router.push('/checkout');
    } else {
      messenger
        ..hideCurrentSnackBar()
        ..showSnackBar(
          SnackBar(
            content: Text(_t('addToCartFailed')),
            backgroundColor: AppColors.error,
            duration: const Duration(seconds: 3),
          ),
        );
    }
  }

  // ──────────────────────────────────────────────
  // Loading
  // ──────────────────────────────────────────────

  Widget _buildLoadingSkeleton() {
    return SingleChildScrollView(
      physics: const NeverScrollableScrollPhysics(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const AspectRatio(
            aspectRatio: _galleryAspectRatio,
            child: ShimmerLoading(height: double.infinity, borderRadius: 0),
          ),
          Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const ShimmerLoading(height: 22, width: 240),
                const SizedBox(height: 12),
                const ShimmerLoading(height: 26, width: 140),
                const SizedBox(height: 18),
                const ShimmerLoading(height: 14),
                const SizedBox(height: 8),
                const ShimmerLoading(height: 14, width: 260),
                const SizedBox(height: 26),
                const ShimmerLoading(height: 16, width: 110),
                const SizedBox(height: 12),
                Row(
                  children: List.generate(
                    3,
                    (_) => const Padding(
                      padding: EdgeInsets.only(right: 12),
                      child: ShimmerLoading(height: 68, width: 68),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

// ──────────────────────────────────────────────
// Variant helpers
// ──────────────────────────────────────────────

class _Selection {
  final VariantMatrix? matrix;
  final ProductVariant? variant;
  final double price;
  final double originalPrice;
  final int discountPercent;
  final bool inStock;
  final int stock;

  const _Selection({
    required this.matrix,
    required this.variant,
    required this.price,
    required this.originalPrice,
    required this.discountPercent,
    required this.inStock,
    required this.stock,
  });

  bool get hasDiscount => discountPercent > 0 && originalPrice > price;
}

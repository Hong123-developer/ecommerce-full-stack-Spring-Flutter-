import 'package:dio/dio.dart';
import 'package:ecomerce_shop/config/api_config.dart';
import 'package:ecomerce_shop/config/theme.dart';
import 'package:ecomerce_shop/models/models.dart';
import 'package:ecomerce_shop/providers/providers.dart';
import 'package:ecomerce_shop/screens/product/product_detail_screen.dart';
import 'package:ecomerce_shop/services/api_client.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:shared_preferences/shared_preferences.dart';

ProductVariant _variant(
  int id,
  List<String> options,
  double price, {
  int stock = 10,
  double? originalPrice,
}) {
  return ProductVariant(
    id: id,
    options: options,
    label: options.join(' / '),
    price: price,
    originalPrice: originalPrice ?? price,
    discountPercent: 0,
    stockQuantity: stock,
    inStock: stock > 0,
    enabled: true,
  );
}

final _watch = Product(
  id: 1,
  name: 'MVMT Minimalist Watch',
  description: 'A modern minimalist watch with a sleek design. Perfect for any occasion.',
  price: 79.99,
  originalPrice: 99.99,
  discountPercent: 20,
  stockQuantity: 30,
  inStock: true,
  galleryUrls: const [],
  optionNames: const ['Color', 'Size'],
  variants: [
    _variant(11, ['Black', 'One Size'], 79.99, originalPrice: 99.99),
    _variant(12, ['Brown', 'One Size'], 84.99, originalPrice: 99.99),
    _variant(13, ['Silver', 'One Size'], 89.99, stock: 0),
  ],
  enabled: true,
  rating: 4.6,
  reviewCount: 128,
);

List<Override> _overrides() => [
  productDetailProvider(1).overrideWith((ref) async => _watch),
  reviewsProvider(1).overrideWith(
    (ref) async => PageData<Review>(
      content: [
        Review(
          id: 1,
          rating: 5,
          comment: 'Looks great and feels solid.',
          authorName: 'Dara',
          verifiedPurchase: true,
          createdAt: '2026-10-01T10:00:00',
          mine: false,
        ),
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    ),
  ),
  reviewSummaryProvider(1).overrideWith(
    (ref) async => ReviewSummary(
      average: 4.6,
      count: 128,
      distribution: const {'5': 100, '4': 20, '3': 5, '2': 2, '1': 1},
    ),
  ),
];

/// A signed-in customer, without secure storage or network.
class _SignedInAuth extends AuthNotifier {
  @override
  AuthState build() => AuthState(
    status: AuthStatus.authenticated,
    user: User(
      id: 7,
      fullName: 'Dara',
      email: 'dara@example.com',
      provider: 'LOCAL',
      emailVerified: true,
      enabled: true,
      roles: const ['CUSTOMER'],
    ),
  );
}

/// A backend that accepts every request and records it.
Dio _acceptingBackend(List<String> requests) {
  return Dio()
    ..interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) {
          requests.add(
            '${options.method} ${options.path}'
            '${options.data == null ? '' : ' ${options.data}'}',
          );
          handler.resolve(
            Response(
              requestOptions: options,
              statusCode: 200,
              data: {'data': <String, dynamic>{}},
            ),
          );
        },
      ),
    );
}

/// Mirrors the app's navigation: bottom-nav tabs in a shell, with product
/// detail and checkout pushed on top.
Future<GoRouter> _pumpInApp(
  WidgetTester tester,
  List<Override> overrides,
) async {
  final router = GoRouter(
    initialLocation: '/shop',
    routes: [
      StatefulShellRoute.indexedStack(
        builder: (context, state, shell) => Scaffold(body: shell),
        branches: [
          StatefulShellBranch(
            routes: [
              GoRoute(path: '/shop', builder: (_, _) => const Text('Shop tab')),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(path: '/cart', builder: (_, _) => const Text('Cart tab')),
            ],
          ),
        ],
      ),
      GoRoute(
        path: '/products/:id',
        builder: (_, state) => ProductDetailScreen(
          productId: int.parse(state.pathParameters['id']!),
        ),
      ),
      GoRoute(
        path: '/checkout',
        builder: (_, _) => const Scaffold(body: Text('Checkout page')),
      ),
    ],
  );
  addTearDown(router.dispose);

  await tester.pumpWidget(
    ProviderScope(
      overrides: overrides,
      child: MaterialApp.router(
        theme: AppTheme.getTheme(AppThemeStyle.monochrome),
        routerConfig: router,
      ),
    ),
  );
  await tester.pumpAndSettle();
  return router;
}

Future<void> _pumpScreen(
  WidgetTester tester, {
  AppThemeStyle style = AppThemeStyle.monochrome,
  double width = 390,
}) async {
  tester.view.devicePixelRatio = 3;
  tester.view.physicalSize = Size(width * 3, 844 * 3);
  addTearDown(tester.view.reset);

  await tester.pumpWidget(
    ProviderScope(
      overrides: _overrides(),
      child: MaterialApp(
        theme: AppTheme.getTheme(style),
        home: const ProductDetailScreen(productId: 1),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  setUpAll(() {
    GoogleFonts.config.allowRuntimeFetching = false;
  });

  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  testWidgets('shows the product like the reference design', (tester) async {
    await _pumpScreen(tester);

    expect(find.text('MVMT Minimalist Watch'), findsOneWidget);
    expect(find.text('-20%'), findsOneWidget);
    expect(find.text('\$99.99'), findsOneWidget);
    expect(find.text('(128 reviews)'), findsOneWidget);
    expect(
      find.textContaining('Color: Black', findRichText: true),
      findsOneWidget,
    );
    expect(
      find.textContaining('Size: One Size', findRichText: true),
      findsOneWidget,
    );
    expect(find.text('In Stock'), findsOneWidget);
    expect(find.text('Add to Cart'), findsOneWidget);
    expect(find.text('Buy Now'), findsOneWidget);
  });

  testWidgets('choosing a color switches variant and price', (tester) async {
    await _pumpScreen(tester);

    await tester.ensureVisible(find.text('Brown'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Brown'));
    await tester.pumpAndSettle();

    expect(
      find.textContaining('Color: Brown', findRichText: true),
      findsOneWidget,
    );
    // Price row and bottom bar.
    expect(find.text('\$84.99'), findsNWidgets(2));
  });

  testWidgets('out-of-stock variant replaces the buy buttons', (tester) async {
    await _pumpScreen(tester);

    await tester.ensureVisible(find.text('Silver'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Silver'));
    await tester.pumpAndSettle();

    expect(find.text('Add to Cart'), findsNothing);
    expect(find.text('Buy Now'), findsNothing);
    final button = tester.widget<FilledButton>(find.byType(FilledButton));
    expect(button.onPressed, isNull);
  });

  // Regression: /cart is a bottom-nav tab, and pushing it on top of the tab
  // shell crashed with "!keyReservation.contains(key)".
  testWidgets('cart icon opens the cart tab without a navigator crash', (
    tester,
  ) async {
    final router = await _pumpInApp(tester, _overrides());
    router.push('/products/1');
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Cart'));
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.text('Cart tab'), findsOneWidget);
  });

  testWidgets('Buy Now adds the chosen variant and opens checkout', (
    tester,
  ) async {
    final requests = <String>[];
    final router = await _pumpInApp(tester, [
      ..._overrides(),
      authProvider.overrideWith(_SignedInAuth.new),
      dioProvider.overrideWithValue(_acceptingBackend(requests)),
    ]);
    router.push('/products/1');
    await tester.pumpAndSettle();

    await tester.tap(find.text('Buy Now'));
    await tester.pumpAndSettle();

    expect(
      requests,
      contains(
        'POST ${ApiConfig.cartItems} '
        '{productId: 1, quantity: 1, variantId: 11}',
      ),
    );
    expect(find.text('Checkout page'), findsOneWidget);
  });

  for (final style in AppThemeStyle.values) {
    testWidgets('lays out without overflow at 320px in ${style.name}', (
      tester,
    ) async {
      await _pumpScreen(tester, style: style, width: 320);
      expect(tester.takeException(), isNull);
    });
  }
}

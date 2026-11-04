import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../config/api_config.dart';
import '../models/models.dart';
import '../services/api_client.dart';
export 'theme_provider.dart';
export 'language_provider.dart';

// ──────────────────────────────────────────────
// Auth State
// ──────────────────────────────────────────────
enum AuthStatus { initial, authenticated, unauthenticated, loading }

class AuthState {
  final AuthStatus status;
  final User? user;
  final String? error;

  const AuthState({this.status = AuthStatus.initial, this.user, this.error});

  AuthState copyWith({AuthStatus? status, User? user, String? error}) {
    return AuthState(
      status: status ?? this.status,
      user: user ?? this.user,
      error: error,
    );
  }
}

class AuthNotifier extends Notifier<AuthState> {
  @override
  AuthState build() {
    Future.microtask(() => checkAuth());
    return const AuthState();
  }

  Dio get _dio => ref.read(dioProvider);

  Future<void> checkAuth() async {
    final token = await getAccessToken();
    if (token == null) {
      state = const AuthState(status: AuthStatus.unauthenticated);
      return;
    }
    try {
      final response = await _dio.get(ApiConfig.me);
      final user = User.fromJson(response.data['data']);
      state = AuthState(status: AuthStatus.authenticated, user: user);
    } catch (_) {
      await clearTokens();
      state = const AuthState(status: AuthStatus.unauthenticated);
    }
  }

  /// HTTP status of the last failed [login], e.g. 403 for an unverified email.
  int? lastLoginErrorStatus;

  // Like register, leaves the status alone until it succeeds: a loading status
  // makes the router redirect to '/', so a failed login would land on home.
  Future<bool> login(String email, String password) async {
    state = state.copyWith(error: null);
    lastLoginErrorStatus = null;
    try {
      final response = await _dio.post(
        ApiConfig.login,
        data: {'email': email, 'password': password},
      );
      final authData = AuthData.fromJson(response.data['data']);
      await saveTokens(
        accessToken: authData.accessToken,
        refreshToken: authData.refreshToken,
      );
      state = AuthState(status: AuthStatus.authenticated, user: authData.user);
      return true;
    } on DioException catch (e) {
      final apiError = ApiException.fromDioError(e);
      lastLoginErrorStatus = apiError.statusCode;
      state = state.copyWith(error: apiError.message);
      return false;
    }
  }

  // Leaves the auth status alone: flipping it to loading makes the router
  // redirect to '/', which would kick the user off the sign-up flow.
  Future<bool> register(
    String fullName,
    String email,
    String phone,
    String password,
  ) async {
    state = state.copyWith(error: null);
    try {
      await _dio.post(
        ApiConfig.register,
        data: {
          'fullName': fullName,
          'email': email,
          'phone': phone,
          'password': password,
        },
      );
      return true;
    } on DioException catch (e) {
      state = state.copyWith(error: ApiException.fromDioError(e).message);
      return false;
    }
  }

  Future<bool> verifyEmail(String email, String code) async {
    try {
      await _dio.post(
        ApiConfig.verifyEmail,
        data: {'email': email, 'code': code},
      );
      return true;
    } on DioException catch (e) {
      state = state.copyWith(error: ApiException.fromDioError(e).message);
      return false;
    }
  }

  Future<bool> resendVerificationCode(String email) async {
    try {
      await _dio.post(ApiConfig.resendCode, data: {'email': email});
      return true;
    } on DioException catch (e) {
      state = state.copyWith(error: ApiException.fromDioError(e).message);
      return false;
    }
  }

  // The backend answers the same way for unknown emails, so success here only
  // means the request was accepted.
  Future<bool> forgotPassword(String email) async {
    try {
      await _dio.post(ApiConfig.forgotPassword, data: {'email': email});
      return true;
    } on DioException catch (e) {
      state = state.copyWith(error: ApiException.fromDioError(e).message);
      return false;
    }
  }

  Future<bool> resetPassword(
    String email,
    String code,
    String newPassword,
  ) async {
    try {
      await _dio.post(
        ApiConfig.resetPassword,
        data: {'email': email, 'code': code, 'newPassword': newPassword},
      );
      return true;
    } on DioException catch (e) {
      state = state.copyWith(error: ApiException.fromDioError(e).message);
      return false;
    }
  }

  Future<void> logout() async {
    try {
      final refreshTk = await getRefreshToken();
      if (refreshTk != null) {
        await _dio.post(ApiConfig.logout, data: {'refreshToken': refreshTk});
      }
    } catch (_) {}
    await clearTokens();
    state = const AuthState(status: AuthStatus.unauthenticated);
  }

  Future<void> refreshUser() async {
    try {
      final response = await _dio.get(ApiConfig.me);
      final user = User.fromJson(response.data['data']);
      state = state.copyWith(user: user);
    } catch (_) {}
  }

  Future<bool> updateProfile({
    required String fullName,
    required String phone,
    String? profileImage,
  }) async {
    try {
      final response = await _dio.put(
        ApiConfig.profile,
        data: {
          'fullName': fullName,
          'phone': phone,
          'profileImage': profileImage,
        },
      );
      state = state.copyWith(user: User.fromJson(response.data['data']));
      return true;
    } on DioException catch (e) {
      state = state.copyWith(error: ApiException.fromDioError(e).message);
      return false;
    }
  }

  /// Uploads the image and returns its public URL. It does not change the
  /// profile by itself — pass the URL to [updateProfile].
  Future<String?> uploadAvatar(List<int> bytes, String filename) async {
    try {
      final response = await _dio.post(
        ApiConfig.profileAvatar,
        data: FormData.fromMap({
          'file': MultipartFile.fromBytes(bytes, filename: filename),
        }),
      );
      return response.data['data']['url'] as String?;
    } on DioException catch (e) {
      state = state.copyWith(error: ApiException.fromDioError(e).message);
      return null;
    }
  }
}

final authProvider = NotifierProvider<AuthNotifier, AuthState>(
  AuthNotifier.new,
);

/// The signed-in customer's id, or null when signed out.
///
/// Every provider that holds per-customer data (orders, cart, wishlist,
/// addresses, liked flags, ...) watches this, so its cached data is dropped
/// and refetched whenever a different customer signs in or the user signs
/// out. Without it, the next account would see the previous one's data.
final currentUserIdProvider = Provider<int?>((ref) {
  return ref.watch(
    authProvider.select(
      (auth) =>
          auth.status == AuthStatus.authenticated ? auth.user?.id : null,
    ),
  );
});

// ──────────────────────────────────────────────
// Categories
// ──────────────────────────────────────────────
final categoriesProvider = FutureProvider<List<Category>>((ref) async {
  final dio = ref.watch(dioProvider);
  final response = await dio.get(
    ApiConfig.categories,
    queryParameters: {'page': 0, 'size': 50, 'sort': 'name,asc'},
  );
  final page = response.data['data'];
  return (page['content'] as List).map((e) => Category.fromJson(e)).toList();
});

// ──────────────────────────────────────────────
// Products
// ──────────────────────────────────────────────
class ProductFilter {
  final String? search;
  final int? categoryId;
  final double? minPrice;
  final double? maxPrice;
  final int page;

  const ProductFilter({
    this.search,
    this.categoryId,
    this.minPrice,
    this.maxPrice,
    this.page = 0,
  });

  ProductFilter copyWith({
    String? search,
    int? categoryId,
    double? minPrice,
    double? maxPrice,
    int? page,
    bool clearCategory = false,
    bool clearSearch = false,
  }) {
    return ProductFilter(
      search: clearSearch ? null : (search ?? this.search),
      categoryId: clearCategory ? null : (categoryId ?? this.categoryId),
      minPrice: minPrice ?? this.minPrice,
      maxPrice: maxPrice ?? this.maxPrice,
      page: page ?? this.page,
    );
  }
}

class ProductFilterNotifier extends Notifier<ProductFilter> {
  @override
  ProductFilter build() => const ProductFilter();

  @override
  ProductFilter get state => super.state;

  @override
  set state(ProductFilter value) => super.state = value;
}

final productFilterProvider =
    NotifierProvider<ProductFilterNotifier, ProductFilter>(
      ProductFilterNotifier.new,
    );

final productsProvider = FutureProvider<PageData<Product>>((ref) async {
  ref.watch(currentUserIdProvider);
  final dio = ref.watch(dioProvider);
  final filter = ref.watch(productFilterProvider);
  final params = <String, dynamic>{
    'page': filter.page,
    'size': 20,
    'sort': 'createdAt,desc',
  };
  if (filter.search != null && filter.search!.isNotEmpty)
    params['search'] = filter.search;
  if (filter.categoryId != null) params['categoryId'] = filter.categoryId;
  if (filter.minPrice != null) params['minPrice'] = filter.minPrice;
  if (filter.maxPrice != null) params['maxPrice'] = filter.maxPrice;

  final response = await dio.get(ApiConfig.products, queryParameters: params);
  return PageData.fromJson(response.data['data'], Product.fromJson);
});

class PaginatedProductsState {
  final List<Product> products;
  final int page;
  final int totalElements;
  final int totalPages;
  final bool isLoading;
  final bool isLoadingMore;
  final bool hasMore;
  final String? error;

  const PaginatedProductsState({
    this.products = const [],
    this.page = 0,
    this.totalElements = 0,
    this.totalPages = 0,
    this.isLoading = false,
    this.isLoadingMore = false,
    this.hasMore = false,
    this.error,
  });

  PaginatedProductsState copyWith({
    List<Product>? products,
    int? page,
    int? totalElements,
    int? totalPages,
    bool? isLoading,
    bool? isLoadingMore,
    bool? hasMore,
    String? error,
  }) {
    return PaginatedProductsState(
      products: products ?? this.products,
      page: page ?? this.page,
      totalElements: totalElements ?? this.totalElements,
      totalPages: totalPages ?? this.totalPages,
      isLoading: isLoading ?? this.isLoading,
      isLoadingMore: isLoadingMore ?? this.isLoadingMore,
      hasMore: hasMore ?? this.hasMore,
      error: error,
    );
  }
}

class PaginatedProductsNotifier extends Notifier<PaginatedProductsState> {
  Dio get _dio => ref.read(dioProvider);

  @override
  PaginatedProductsState build() {
    // Products carry the customer's liked flags, so reload on account change.
    ref.watch(currentUserIdProvider);
    ref.listen(productFilterProvider, (_, __) {
      fetchFirstPage();
    });
    Future.microtask(() => fetchFirstPage());
    return const PaginatedProductsState(isLoading: true);
  }

  Future<void> fetchFirstPage() async {
    if (!ref.mounted) return;
    state = state.copyWith(isLoading: true, error: null);
    try {
      final filter = ref.read(productFilterProvider);
      final params = <String, dynamic>{
        'page': 0,
        'size': 20,
        'sort': 'createdAt,desc',
      };
      if (filter.search != null && filter.search!.isNotEmpty)
        params['search'] = filter.search;
      if (filter.categoryId != null) params['categoryId'] = filter.categoryId;
      if (filter.minPrice != null) params['minPrice'] = filter.minPrice;
      if (filter.maxPrice != null) params['maxPrice'] = filter.maxPrice;

      final res = await _dio.get(ApiConfig.products, queryParameters: params);
      if (!ref.mounted) return;
      final pageData = PageData.fromJson(res.data['data'], Product.fromJson);

      state = PaginatedProductsState(
        products: pageData.content,
        page: 0,
        totalElements: pageData.totalElements,
        totalPages: pageData.totalPages,
        hasMore: !pageData.last,
        isLoading: false,
        isLoadingMore: false,
      );
    } catch (e) {
      if (!ref.mounted) return;
      state = state.copyWith(isLoading: false, error: e.toString());
    }
  }

  Future<void> loadMore() async {
    if (state.isLoading || state.isLoadingMore || !state.hasMore) return;

    state = state.copyWith(isLoadingMore: true);
    final nextPage = state.page + 1;
    try {
      final filter = ref.read(productFilterProvider);
      final params = <String, dynamic>{
        'page': nextPage,
        'size': 20,
        'sort': 'createdAt,desc',
      };
      if (filter.search != null && filter.search!.isNotEmpty)
        params['search'] = filter.search;
      if (filter.categoryId != null) params['categoryId'] = filter.categoryId;
      if (filter.minPrice != null) params['minPrice'] = filter.minPrice;
      if (filter.maxPrice != null) params['maxPrice'] = filter.maxPrice;

      final res = await _dio.get(ApiConfig.products, queryParameters: params);
      if (!ref.mounted) return;
      final pageData = PageData.fromJson(res.data['data'], Product.fromJson);

      final combined = [...state.products, ...pageData.content];
      final uniqueMap = {for (var p in combined) p.id: p};

      state = state.copyWith(
        products: uniqueMap.values.toList(),
        page: nextPage,
        totalElements: pageData.totalElements,
        totalPages: pageData.totalPages,
        hasMore: !pageData.last,
        isLoadingMore: false,
      );
    } catch (e) {
      if (!ref.mounted) return;
      state = state.copyWith(isLoadingMore: false, error: e.toString());
    }
  }
}

final paginatedProductsProvider =
    NotifierProvider<PaginatedProductsNotifier, PaginatedProductsState>(
      PaginatedProductsNotifier.new,
    );

// Home feed: newest products, independent of the Shop tab's search/category filter.
final homeProductsProvider = FutureProvider<List<Product>>((ref) async {
  ref.watch(currentUserIdProvider);
  final dio = ref.watch(dioProvider);
  final response = await dio.get(
    ApiConfig.products,
    queryParameters: {'page': 0, 'size': 100, 'sort': 'createdAt,desc'},
  );
  return PageData.fromJson(response.data['data'], Product.fromJson).content;
});

final productDetailProvider = FutureProvider.family<Product, int>((
  ref,
  id,
) async {
  ref.watch(currentUserIdProvider);
  final dio = ref.watch(dioProvider);
  final response = await dio.get('${ApiConfig.products}/$id');
  return Product.fromJson(response.data['data']);
});

// ──────────────────────────────────────────────
// Cart
// ──────────────────────────────────────────────
class CartNotifier extends Notifier<Cart> {
  @override
  Cart build() {
    // A different customer means a different cart: start empty, load theirs.
    final userId = ref.watch(currentUserIdProvider);
    if (userId != null) Future.microtask(fetchCart);
    return Cart.empty();
  }

  Dio get _dio => ref.read(dioProvider);

  Future<void> fetchCart() async {
    try {
      final response = await _dio.get(ApiConfig.cart);
      // The customer may have changed while the request was in flight.
      if (!ref.mounted) return;
      state = Cart.fromJson(response.data['data']);
    } catch (_) {
      if (ref.mounted) state = Cart.empty();
    }
  }

  Future<bool> addItem(int productId, int quantity, {int? variantId}) async {
    try {
      final data = <String, dynamic>{
        'productId': productId,
        'quantity': quantity,
      };
      if (variantId != null) data['variantId'] = variantId;
      await _dio.post(ApiConfig.cartItems, data: data);
      await fetchCart();
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<void> updateQuantity(int cartItemId, int quantity) async {
    try {
      await _dio.put(
        '${ApiConfig.cartItems}/$cartItemId',
        data: {'quantity': quantity},
      );
      await fetchCart();
    } catch (_) {}
  }

  Future<void> removeItem(int cartItemId) async {
    try {
      await _dio.delete('${ApiConfig.cartItems}/$cartItemId');
      await fetchCart();
    } catch (_) {}
  }

  Future<void> clearCart() async {
    try {
      await _dio.delete(ApiConfig.cart);
      if (ref.mounted) state = Cart.empty();
    } catch (_) {}
  }
}

final cartProvider = NotifierProvider<CartNotifier, Cart>(CartNotifier.new);

// ──────────────────────────────────────────────
// Orders
// ──────────────────────────────────────────────
final ordersProvider = FutureProvider<List<Order>>((ref) async {
  ref.watch(currentUserIdProvider);
  final dio = ref.watch(dioProvider);
  final response = await dio.get(
    ApiConfig.orders,
    queryParameters: {'page': 0, 'size': 50, 'sort': 'createdAt,desc'},
  );
  final page = response.data['data'];
  return (page['content'] as List).map((e) => Order.fromJson(e)).toList();
});

final paymentsProvider = FutureProvider<List<PaymentData>>((ref) async {
  ref.watch(currentUserIdProvider);
  final response = await ref
      .watch(dioProvider)
      .get(
        ApiConfig.payments,
        queryParameters: {'page': 0, 'size': 50, 'sort': 'createdAt,desc'},
      );
  final page = response.data['data'];
  return (page['content'] as List).map((e) => PaymentData.fromJson(e)).toList();
});

final orderDetailProvider = FutureProvider.family<Order, int>((ref, id) async {
  ref.watch(currentUserIdProvider);
  final dio = ref.watch(dioProvider);
  final response = await dio.get('${ApiConfig.orders}/$id');
  return Order.fromJson(response.data['data']);
});

final orderTrackingProvider = FutureProvider.family<OrderTracking, int>((
  ref,
  id,
) async {
  ref.watch(currentUserIdProvider);
  final response = await ref
      .watch(dioProvider)
      .get(ApiConfig.orderTracking(id));
  return OrderTracking.fromJson(response.data['data']);
});

final orderInvoiceProvider = FutureProvider.family<Invoice, int>((
  ref,
  id,
) async {
  ref.watch(currentUserIdProvider);
  final response = await ref.watch(dioProvider).get(ApiConfig.orderInvoice(id));
  return Invoice.fromJson(response.data['data']);
});

// ──────────────────────────────────────────────
// Addresses
// ──────────────────────────────────────────────
final addressesProvider = FutureProvider<List<Address>>((ref) async {
  ref.watch(currentUserIdProvider);
  final dio = ref.watch(dioProvider);
  final response = await dio.get(ApiConfig.addresses);
  final data = response.data['data'];
  if (data is List) {
    return data.map((e) => Address.fromJson(e)).toList();
  }
  return [];
});

// ──────────────────────────────────────────────
// Hero Slides
// ──────────────────────────────────────────────
final heroSlidesProvider = FutureProvider<List<HeroSlide>>((ref) async {
  final dio = ref.watch(dioProvider);
  final response = await dio.get(ApiConfig.heroSlides);
  final data = response.data['data'];
  if (data is List) {
    return data.map((e) => HeroSlide.fromJson(e)).toList();
  }
  return [];
});

// ──────────────────────────────────────────────
// Notifications
// ──────────────────────────────────────────────
final notificationsProvider = FutureProvider<List<AppNotification>>((
  ref,
) async {
  ref.watch(currentUserIdProvider);
  final dio = ref.watch(dioProvider);
  final response = await dio.get(
    ApiConfig.notifications,
    queryParameters: {'page': 0, 'size': 50},
  );
  final page = response.data['data'];
  return (page['content'] as List)
      .map((e) => AppNotification.fromJson(e))
      .toList();
});

final unreadCountProvider = FutureProvider<int>((ref) async {
  ref.watch(currentUserIdProvider);
  final dio = ref.watch(dioProvider);
  try {
    final response = await dio.get(ApiConfig.unreadCount);
    return response.data['data']['count'] ?? 0;
  } catch (_) {
    return 0;
  }
});

// ──────────────────────────────────────────────
// Delivery Options
// ──────────────────────────────────────────────
final deliveryOptionsProvider = FutureProvider<List<DeliveryOption>>((
  ref,
) async {
  final dio = ref.watch(dioProvider);
  final response = await dio.get(ApiConfig.deliveryOptions);
  final data = response.data['data'];
  if (data is List) {
    return data.map((e) => DeliveryOption.fromJson(e)).toList();
  }
  return [];
});

// ──────────────────────────────────────────────
// Reviews
// ──────────────────────────────────────────────
final reviewsProvider = FutureProvider.family<PageData<Review>, int>((
  ref,
  productId,
) async {
  ref.watch(currentUserIdProvider);
  final dio = ref.watch(dioProvider);
  final response = await dio.get(
    '${ApiConfig.products}/$productId/reviews',
    queryParameters: {'page': 0, 'size': 20, 'sort': 'newest'},
  );
  return PageData.fromJson(response.data['data'], Review.fromJson);
});

final reviewSummaryProvider = FutureProvider.family<ReviewSummary, int>((
  ref,
  productId,
) async {
  final dio = ref.watch(dioProvider);
  final response = await dio.get(
    '${ApiConfig.products}/$productId/reviews/summary',
  );
  return ReviewSummary.fromJson(response.data['data']);
});

// ──────────────────────────────────────────────
// Wishlist (Product Likes)
// ──────────────────────────────────────────────

/// Tracks the liked state of products and allows toggling.
/// Stores local state so the heart animation is instant, then syncs with the API.
class WishlistNotifier extends Notifier<Map<int, bool>> {
  @override
  Map<int, bool> build() {
    // Liked flags belong to the signed-in customer.
    ref.watch(currentUserIdProvider);
    return {};
  }

  Dio get _dio => ref.read(dioProvider);

  /// Toggle a product's liked state. Returns true if now liked, false if unliked.
  ///
  /// [currentlyLiked] is the server's flag for the product, used until this
  /// session has its own record (e.g. right after a customer signs in).
  Future<bool> toggle(int productId, {bool currentlyLiked = false}) async {
    final wasLiked = state[productId] ?? currentlyLiked;
    // Optimistic update
    state = {...state, productId: !wasLiked};
    try {
      await _dio.post('${ApiConfig.products}/$productId/like');
      if (!ref.mounted) return !wasLiked;
      // Refresh related providers so counts update
      ref.invalidate(productsProvider);
      ref.invalidate(homeProductsProvider);
      ref.invalidate(productDetailProvider(productId));
      ref.invalidate(wishlistPageProvider);
      return !wasLiked;
    } catch (_) {
      // Revert on failure
      if (ref.mounted) state = {...state, productId: wasLiked};
      return wasLiked;
    }
  }

  /// Seed liked state from a product response (e.g. from browse/detail).
  void seed(int productId, bool liked) {
    if (state[productId] != liked) {
      state = {...state, productId: liked};
    }
  }

  /// Returns the effective liked state, preferring local override.
  bool isLiked(int productId, {bool serverValue = false}) {
    return state.containsKey(productId) ? state[productId]! : serverValue;
  }
}

final wishlistProvider = NotifierProvider<WishlistNotifier, Map<int, bool>>(
  WishlistNotifier.new,
);

/// Paginated wishlist page for the My Wishlist screen.
final wishlistPageProvider = FutureProvider<List<Product>>((ref) async {
  ref.watch(currentUserIdProvider);
  final dio = ref.watch(dioProvider);
  try {
    final response = await dio.get(
      ApiConfig.myLikes,
      queryParameters: {'page': 0, 'size': 50, 'sort': 'createdAt,desc'},
    );
    final data = response.data['data'];
    final items = data['content'] as List? ?? [];
    return items.map((e) {
      return Product(
        id: e['productId'] ?? 0,
        name: e['productName'] ?? '',
        price: (e['productPrice'] ?? 0).toDouble(),
        originalPrice: (e['productPrice'] ?? 0).toDouble(),
        discountPercent: 0,
        imageUrl: e['productImageUrl'],
        inStock: e['inStock'] ?? false,
        liked: e['liked'] ?? true,
        likeCount: e['likeCount'] ?? 0,
        stockQuantity: 0,
        galleryUrls: [],
        optionNames: [],
        variants: [],
        enabled: true,
        rating: 0,
        reviewCount: 0,
      );
    }).toList();
  } catch (_) {
    return [];
  }
});

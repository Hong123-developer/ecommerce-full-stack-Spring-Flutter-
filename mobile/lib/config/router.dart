import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../providers/providers.dart';
import '../screens/auth/forgot_password_screen.dart';
import '../screens/auth/login_screen.dart';
import '../screens/auth/oauth2_callback_screen.dart';
import '../screens/auth/register_screen.dart';
import '../screens/auth/reset_password_screen.dart';
import '../screens/auth/verify_email_screen.dart';
import '../screens/cart/cart_screen.dart';
import '../screens/checkout/checkout_screen.dart';
import '../screens/home/home_screen.dart';
import '../screens/main_shell.dart';
import '../screens/notifications/notifications_screen.dart';
import '../screens/orders/order_detail_screen.dart';
import '../screens/orders/orders_screen.dart';
import '../screens/payments/payments_screen.dart';
import '../screens/product/product_detail_screen.dart';
import '../screens/product/products_screen.dart';
import '../screens/profile/personal_details_screen.dart';
import '../screens/profile/profile_screen.dart';
import '../screens/product/wishlist_screen.dart';
import '../screens/splash_screen.dart';

class _RouterRefreshNotifier extends ChangeNotifier {
  _RouterRefreshNotifier(Ref ref) {
    ref.listen<AuthStatus>(authProvider.select((s) => s.status), (prev, next) {
      if (prev != next) {
        notifyListeners();
      }
    });
  }
}

final routerProvider = Provider<GoRouter>((ref) {
  // Keep navigator keys owned by this router instance. During web hot reload,
  // the old and new MaterialApp.router trees can briefly coexist.
  final rootNavigatorKey = GlobalKey<NavigatorState>(debugLabel: 'root');
  final shellHomeKey = GlobalKey<NavigatorState>(debugLabel: 'shell_home');
  final shellShopKey = GlobalKey<NavigatorState>(debugLabel: 'shell_shop');
  final shellWishlistKey = GlobalKey<NavigatorState>(
    debugLabel: 'shell_wishlist',
  );
  final shellCartKey = GlobalKey<NavigatorState>(debugLabel: 'shell_cart');
  final shellProfileKey = GlobalKey<NavigatorState>(
    debugLabel: 'shell_profile',
  );
  final refreshNotifier = _RouterRefreshNotifier(ref);
  ref.onDispose(refreshNotifier.dispose);

  return GoRouter(
    navigatorKey: rootNavigatorKey,
    initialLocation: '/',
    refreshListenable: refreshNotifier,
    redirect: (context, state) {
      final authStatus = ref.read(authProvider).status;
      final isAuth = authStatus == AuthStatus.authenticated;
      final isLoading =
          authStatus == AuthStatus.initial || authStatus == AuthStatus.loading;
      final location = state.matchedLocation;

      final isLoginRoute =
          location == '/login' ||
          location == '/register' ||
          location == '/verify-email' ||
          location == '/forgot-password' ||
          location == '/verify-reset-code' ||
          location == '/reset-password' ||
          location == '/oauth2/callback';

      final isPublicRoute =
          location == '/' ||
          location.startsWith('/home') ||
          location.startsWith('/products') ||
          location.startsWith('/shop');

      // Auth routes stay put while the stored session is checked: a web page load at
      // /oauth2/callback?accessToken=... must reach its screen, not be sent to '/'.
      if (isLoading && !isLoginRoute && !isPublicRoute) return '/';
      if (!isAuth && !isLoading && !isLoginRoute && !isPublicRoute) {
        return '/login';
      }
      if (isAuth && isLoginRoute) return '/home';
      return null;
    },
    routes: [
      GoRoute(
        path: '/',
        parentNavigatorKey: rootNavigatorKey,
        builder: (context, state) => const SplashScreen(),
      ),
      GoRoute(
        path: '/login',
        parentNavigatorKey: rootNavigatorKey,
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        path: '/register',
        parentNavigatorKey: rootNavigatorKey,
        builder: (context, state) => const RegisterScreen(),
      ),
      GoRoute(
        path: '/verify-email',
        parentNavigatorKey: rootNavigatorKey,
        builder: (context, state) {
          final email =
              (state.extra as String?) ??
              state.uri.queryParameters['email'] ??
              '';
          return VerifyEmailScreen(email: email);
        },
      ),
      GoRoute(
        path: '/forgot-password',
        parentNavigatorKey: rootNavigatorKey,
        builder: (context, state) =>
            ForgotPasswordScreen(initialEmail: (state.extra as String?) ?? ''),
      ),
      GoRoute(
        path: '/verify-reset-code',
        parentNavigatorKey: rootNavigatorKey,
        // Without an email there is nothing to verify (e.g. after a web reload)
        redirect: (context, state) =>
            state.extra is String ? null : '/forgot-password',
        builder: (context, state) => VerifyEmailScreen(
          email: state.extra as String,
          purpose: OtpPurpose.passwordReset,
        ),
      ),
      GoRoute(
        path: '/reset-password',
        parentNavigatorKey: rootNavigatorKey,
        redirect: (context, state) =>
            state.extra is Map ? null : '/forgot-password',
        builder: (context, state) {
          final args = state.extra as Map;
          return ResetPasswordScreen(
            email: args['email'] as String,
            code: args['code'] as String,
          );
        },
      ),
      GoRoute(
        path: '/oauth2/callback',
        parentNavigatorKey: rootNavigatorKey,
        builder: (context, state) {
          final accessToken = state.uri.queryParameters['accessToken'];
          final refreshToken = state.uri.queryParameters['refreshToken'];
          final error = state.uri.queryParameters['error'];
          return OAuth2CallbackScreen(
            accessToken: accessToken,
            refreshToken: refreshToken,
            error: error,
          );
        },
      ),
      // Main shell with bottom nav
      StatefulShellRoute.indexedStack(
        parentNavigatorKey: rootNavigatorKey,
        builder: (context, state, navigationShell) {
          return MainShell(navigationShell: navigationShell);
        },
        branches: [
          StatefulShellBranch(
            navigatorKey: shellHomeKey,
            routes: [
              GoRoute(
                path: '/home',
                builder: (context, state) => const HomeScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            navigatorKey: shellShopKey,
            routes: [
              GoRoute(
                path: '/shop',
                builder: (context, state) => const ProductsScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            navigatorKey: shellWishlistKey,
            routes: [
              GoRoute(
                path: '/wishlist',
                builder: (context, state) => const WishlistScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            navigatorKey: shellCartKey,
            routes: [
              GoRoute(
                path: '/cart',
                builder: (context, state) => const CartScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            navigatorKey: shellProfileKey,
            routes: [
              GoRoute(
                path: '/profile',
                builder: (context, state) => const ProfileScreen(),
              ),
            ],
          ),
        ],
      ),
      GoRoute(
        path: '/orders',
        parentNavigatorKey: rootNavigatorKey,
        builder: (context, state) => const OrdersScreen(),
      ),
      GoRoute(
        path: '/payments',
        parentNavigatorKey: rootNavigatorKey,
        builder: (context, state) => const PaymentsScreen(),
      ),
      GoRoute(
        path: '/products/:id',
        parentNavigatorKey: rootNavigatorKey,
        builder: (context, state) {
          final id = int.parse(state.pathParameters['id']!);
          return ProductDetailScreen(productId: id);
        },
      ),
      GoRoute(
        path: '/checkout',
        parentNavigatorKey: rootNavigatorKey,
        builder: (context, state) => const CheckoutScreen(),
      ),
      GoRoute(
        path: '/orders/:id',
        parentNavigatorKey: rootNavigatorKey,
        builder: (context, state) {
          final id = int.parse(state.pathParameters['id']!);
          return OrderDetailScreen(orderId: id);
        },
      ),
      GoRoute(
        path: '/notifications',
        parentNavigatorKey: rootNavigatorKey,
        builder: (context, state) => const NotificationsScreen(),
      ),
      GoRoute(
        path: '/personal-details',
        parentNavigatorKey: rootNavigatorKey,
        builder: (context, state) => const PersonalDetailsScreen(),
      ),
    ],
  );
});

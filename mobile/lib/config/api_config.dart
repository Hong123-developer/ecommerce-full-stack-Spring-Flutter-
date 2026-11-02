import 'package:flutter/foundation.dart';

// ──────────────────────────────────────────────
// API Configuration
// ──────────────────────────────────────────────
class ApiConfig {
  /// Backend host IP:
  /// - Wi-Fi IP: '192.168.0.103' (works on both Android Emulator and physical phone on the same Wi-Fi)
  /// - Android Emulator Loopback: '10.0.2.2'
  /// - Localhost: 'localhost'
  static const String _host = '192.168.0.103';
  static const String _port = '8080';

  static String get baseUrl {
    if (kIsWeb) {
      return 'http://localhost:$_port';
    }
    if (defaultTargetPlatform == TargetPlatform.android) {
      return 'http://$_host:$_port';
    }
    if (defaultTargetPlatform == TargetPlatform.iOS) {
      return 'http://$_host:$_port';
    }
    return 'http://localhost:$_port';
  }

  static const String apiPrefix = '/api/v1';

  // Auth endpoints
  static const String login = '$apiPrefix/auth/login';
  static const String register = '$apiPrefix/auth/register';
  static const String verifyEmail = '$apiPrefix/auth/verify-email';
  static const String resendCode = '$apiPrefix/auth/resend-code';
  static const String refreshToken = '$apiPrefix/auth/refresh';
  static const String me = '$apiPrefix/auth/me';
  static const String changePassword = '$apiPrefix/auth/change-password';
  static const String forgotPassword = '$apiPrefix/auth/forgot-password';
  static const String resetPassword = '$apiPrefix/auth/reset-password';
  static const String logout = '$apiPrefix/auth/logout';
  static const String logoutAll = '$apiPrefix/auth/logout-all';

  // Profile
  static const String profile = '$apiPrefix/me';
  static const String profileAvatar = '$apiPrefix/me/avatar';

  // Categories
  static const String categories = '$apiPrefix/categories';

  // Products
  static const String products = '$apiPrefix/products';

  // Cart
  static const String cart = '$apiPrefix/cart';
  static const String cartItems = '$apiPrefix/cart/items';

  // Orders
  static const String orders = '$apiPrefix/orders';
  static const String checkout = '$apiPrefix/orders/checkout';
  static String orderTracking(int id) => '$orders/$id/tracking';
  static String orderInvoice(int id) => '$orders/$id/invoice';
  static const String couponValidate = '$apiPrefix/coupons/validate';

  // Payments
  static const String payments = '$apiPrefix/payments';

  // Addresses
  static const String addresses = '$apiPrefix/addresses';

  // Notifications
  static const String notifications = '$apiPrefix/notifications';
  static const String unreadCount = '$apiPrefix/notifications/unread-count';

  // Hero Slides
  static const String heroSlides = '$apiPrefix/hero-slides';

  // Store Settings
  static const String storeSettings = '$apiPrefix/store-settings';

  // Delivery Options
  static const String deliveryOptions = '$apiPrefix/delivery-options';

  // Wishlist (Likes)
  static const String myLikes = '$apiPrefix/me/likes';
}
